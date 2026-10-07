// Run these commands from the repository root. On Windows, Docker Desktop is
// started automatically if its daemon is not already available.
//
//	go run .\tools\verify.go
//	go run .\tools\verify.go -mode backend
//	go run .\tools\verify.go -mode frontend
package main

import (
	"bufio"
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"os"
	"os/exec"
	"os/signal"
	"path/filepath"
	"runtime"
	"strings"
	"time"
)

const (
	dockerStartupTimeout   = 2 * time.Minute
	dockerCheckTimeout     = 5 * time.Second
	postgresStartupTimeout = 90 * time.Second
)

func main() {
	mode := flag.String("mode", "verify", "action to run: verify, backend, or frontend")
	skipDocker := flag.Bool("skip-docker", false, "do not start Compose services or wait for PostgreSQL")
	flag.Parse()

	if *mode != "verify" && *mode != "backend" && *mode != "frontend" {
		fail(fmt.Errorf("unknown mode %q; use verify, backend, or frontend", *mode))
	}

	root, err := findRepositoryRoot()
	if err != nil {
		fail(err)
	}

	dockerDirectory := filepath.Join(root, "docker")
	backendDirectory := filepath.Join(root, "TalentPulseAI")
	frontendDirectory := filepath.Join(root, "frontend")

	databaseEnv, err := prepareDatabase(dockerDirectory, *skipDocker)
	if err != nil {
		fail(err)
	}

	backendEnv := map[string]string{}
	if password := os.Getenv("POSTGRES_PASSWORD"); password != "" {
		backendEnv["POSTGRES_PASSWORD"] = password
	} else if databaseEnv != nil {
		backendEnv["POSTGRES_PASSWORD"] = databaseEnv["POSTGRES_PASSWORD"]
	}

	if *mode == "backend" {
		startBackend(backendDirectory, backendEnv)
		return
	}
	if *mode == "frontend" {
		startFrontend(frontendDirectory)
		return
	}

	mavenArgs := []string{"-B", "clean", "verify"}
	if runtime.GOOS == "windows" {
		commandArgs := append([]string{"/c", "mvnw.cmd"}, mavenArgs...)
		if err := run(context.Background(), backendDirectory, backendEnv, "cmd", commandArgs...); err != nil {
			fail(fmt.Errorf("backend verification failed: %w", err))
		}
	} else {
		if err := run(context.Background(), backendDirectory, backendEnv, "./mvnw", mavenArgs...); err != nil {
			fail(fmt.Errorf("backend verification failed: %w", err))
		}
	}

	if err := runNpm(frontendDirectory, "test", "--", "--watch=false"); err != nil {
		fail(fmt.Errorf("frontend tests failed: %w", err))
	}
	if err := runNpm(frontendDirectory, "run", "build"); err != nil {
		fail(fmt.Errorf("frontend production build failed: %w", err))
	}

	fmt.Println("\nAll tests and builds passed.")
}

func prepareDatabase(dockerDirectory string, skipDocker bool) (map[string]string, error) {
	if skipDocker {
		if os.Getenv("POSTGRES_PASSWORD") == "" {
			return nil, errors.New("POSTGRES_PASSWORD must be set when using -skip-docker")
		}
		return nil, nil
	}

	databaseEnv, err := readDotEnv(filepath.Join(dockerDirectory, ".env"))
	if err != nil {
		return nil, fmt.Errorf("read docker/.env: %w", err)
	}
	for _, key := range []string{"POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD"} {
		if databaseEnv[key] == "" {
			return nil, fmt.Errorf("docker/.env must define %s", key)
		}
	}
	if err := ensureDockerDaemon(); err != nil {
		return nil, err
	}
	if err := run(context.Background(), dockerDirectory, nil, "docker", "compose", "--env-file", ".env", "up", "-d"); err != nil {
		return nil, fmt.Errorf("start Docker Compose services: %w", err)
	}
	if err := waitForPostgres(dockerDirectory, databaseEnv); err != nil {
		return nil, err
	}
	return databaseEnv, nil
}

func ensureDockerDaemon() error {
	if _, err := exec.LookPath("docker"); err != nil {
		return fmt.Errorf("Docker CLI not found on PATH: %w", err)
	}
	if dockerDaemonReady() {
		return nil
	}
	if runtime.GOOS != "windows" {
		return errors.New("Docker daemon is not running; start Docker Desktop or the Docker Engine, then retry")
	}

	fmt.Println("Docker daemon is not running; starting Docker Desktop...")
	ctx, cancel := context.WithTimeout(context.Background(), dockerStartupTimeout)
	defer cancel()

	command := exec.CommandContext(ctx, "docker", "desktop", "start")
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	if err := command.Run(); err != nil {
		if dockerDaemonReady() {
			return nil
		}
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			return fmt.Errorf("timed out starting Docker Desktop after %s", dockerStartupTimeout)
		}
		return fmt.Errorf("could not start Docker Desktop; ensure it is installed and supports `docker desktop start`: %w", err)
	}

	deadline := time.Now().Add(dockerStartupTimeout)
	for time.Now().Before(deadline) {
		if dockerDaemonReady() {
			fmt.Println("Docker daemon is ready.")
			return nil
		}
		time.Sleep(time.Second)
	}
	return fmt.Errorf("Docker Desktop was started, but the daemon did not become ready within %s", dockerStartupTimeout)
}

func dockerDaemonReady() bool {
	ctx, cancel := context.WithTimeout(context.Background(), dockerCheckTimeout)
	defer cancel()

	command := exec.CommandContext(ctx, "docker", "info")
	command.Stdout = io.Discard
	command.Stderr = io.Discard
	return command.Run() == nil
}

func startBackend(directory string, environment map[string]string) {
	fmt.Println("Backend API: http://localhost:8081")
	fmt.Println("Press Ctrl+C to stop the backend.")
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	var err error
	if runtime.GOOS == "windows" {
		err = run(ctx, directory, environment, "cmd", "/c", "mvnw.cmd", "spring-boot:run")
	} else {
		err = run(ctx, directory, environment, "./mvnw", "spring-boot:run")
	}
	if ctx.Err() == nil && err != nil {
		fail(fmt.Errorf("backend stopped with an error: %w", err))
	}
}

func startFrontend(directory string) {
	fmt.Println("Frontend: http://localhost:4200")
	fmt.Println("Press Ctrl+C to stop the frontend.")
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt)
	defer stop()

	var err error
	if runtime.GOOS == "windows" {
		err = run(ctx, directory, nil, "cmd", "/c", "npm.cmd", "start", "--", "--host", "127.0.0.1")
	} else {
		err = run(ctx, directory, nil, "npm", "start", "--", "--host", "127.0.0.1")
	}
	if ctx.Err() == nil && err != nil {
		fail(fmt.Errorf("frontend stopped with an error: %w", err))
	}
}

func runNpm(directory string, args ...string) error {
	if runtime.GOOS == "windows" {
		commandArgs := append([]string{"/c", "npm.cmd"}, args...)
		return run(context.Background(), directory, nil, "cmd", commandArgs...)
	}
	return run(context.Background(), directory, nil, "npm", args...)
}

func findRepositoryRoot() (string, error) {
	directory, err := os.Getwd()
	if err != nil {
		return "", fmt.Errorf("get current directory: %w", err)
	}

	for {
		if fileExists(filepath.Join(directory, "docker", "docker-compose.yml")) &&
			fileExists(filepath.Join(directory, "TalentPulseAI", "pom.xml")) &&
			fileExists(filepath.Join(directory, "frontend", "package.json")) {
			return directory, nil
		}
		parent := filepath.Dir(directory)
		if parent == directory {
			return "", errors.New("run this command from the repository or one of its subdirectories")
		}
		directory = parent
	}
}

func fileExists(path string) bool {
	info, err := os.Stat(path)
	return err == nil && !info.IsDir()
}

func readDotEnv(path string) (map[string]string, error) {
	file, err := os.Open(path)
	if err != nil {
		return nil, err
	}
	defer file.Close()

	values := make(map[string]string)
	scanner := bufio.NewScanner(file)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}

		key, value, found := strings.Cut(line, "=")
		if !found {
			continue
		}
		key = strings.TrimSpace(key)
		value = strings.TrimSpace(value)
		if len(value) >= 2 {
			if (value[0] == '"' && value[len(value)-1] == '"') ||
				(value[0] == '\'' && value[len(value)-1] == '\'') {
				value = value[1 : len(value)-1]
			}
		}
		values[key] = value
	}
	if err := scanner.Err(); err != nil {
		return nil, err
	}
	return values, nil
}

func waitForPostgres(directory string, databaseEnv map[string]string) error {
	deadline := time.Now().Add(postgresStartupTimeout)
	for time.Now().Before(deadline) {
		command := exec.Command(
			"docker", "compose", "--env-file", ".env", "exec", "-T", "postgres",
			"pg_isready", "-U", databaseEnv["POSTGRES_USER"], "-d", databaseEnv["POSTGRES_DB"],
		)
		command.Dir = directory
		command.Stdout = io.Discard
		command.Stderr = io.Discard
		if err := command.Run(); err == nil {
			fmt.Println("PostgreSQL is ready.")
			return nil
		}
		time.Sleep(time.Second)
	}
	return fmt.Errorf("PostgreSQL did not become ready within %s", postgresStartupTimeout)
}

func run(ctx context.Context, directory string, environment map[string]string, name string, args ...string) error {
	fmt.Printf("\n> %s %s\n", name, strings.Join(args, " "))
	command := exec.CommandContext(ctx, name, args...)
	command.Dir = directory
	command.Stdout = os.Stdout
	command.Stderr = os.Stderr
	if environment != nil {
		command.Env = mergeEnvironment(os.Environ(), environment)
	}
	return command.Run()
}

func mergeEnvironment(current []string, overrides map[string]string) []string {
	merged := make([]string, 0, len(current)+len(overrides))
	for _, entry := range current {
		key, _, found := strings.Cut(entry, "=")
		if !found {
			merged = append(merged, entry)
			continue
		}
		for override := range overrides {
			if key == override || (runtime.GOOS == "windows" && strings.EqualFold(key, override)) {
				goto nextEntry
			}
		}
		merged = append(merged, entry)
	nextEntry:
	}
	for key, value := range overrides {
		merged = append(merged, key+"="+value)
	}
	return merged
}

func fail(err error) {
	fmt.Fprintln(os.Stderr, "\nBuild pipeline failed:", err)
	os.Exit(1)
}
