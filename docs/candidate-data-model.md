# Candidate data model

Candidate identity and source resume content remain in the `candidates` table.
Normalized recruiter profile data is stored separately in `candidate_profiles`,
linked one-to-one to a candidate. Skills, job titles, education, and locations
are JSONB string lists so they can be queried independently without mixing
normalized values into the source resume text. Profile fields may remain empty
when the source does not support a reliable value.

The current application uses Hibernate `ddl-auto: update`; the new table is an
additive schema change managed by Hibernate alongside the existing schema
bootstrap. This is suitable for the current local/demo setup and avoids
destructive changes to existing candidate data. Before production deployment,
introduce a versioned migration tool such as Flyway or Liquibase and manage
schema changes through reviewed migrations instead of relying on Hibernate
schema updates.

During batch ingestion, PDF text is parsed into the profile using deterministic
labels and section headings. Skill phrases explicitly listed in the resume's
Skills section are retained even when they are not in the built-in alias map;
that map only normalizes known synonyms to a shared label. The parser does not
infer experience from employment dates. Existing candidate API responses
remain unchanged and do not include raw resume text, profile internals, or
storage paths.
