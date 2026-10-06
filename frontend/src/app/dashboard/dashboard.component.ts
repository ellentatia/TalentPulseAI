import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe, DecimalPipe, NgClass, NgFor, NgIf } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { BehaviorSubject, Observable, timer } from 'rxjs';
import { exhaustMap, switchMap, takeWhile } from 'rxjs/operators';
import {
  CandidateIngestionStatus,
  CandidateMatch,
  JobDescriptionResponse,
  JobService,
  ResumeUploadResponse
} from '../core/services/job.service';

interface UploadedCandidateStatus extends CandidateIngestionStatus {
  statusCheckError: boolean;
}

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [AsyncPipe, DecimalPipe, FormsModule, NgClass, NgFor, NgIf, ReactiveFormsModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent implements OnInit {
  private readonly destroyRef = inject(DestroyRef);
  public readonly jobId = new FormControl('', { nonNullable: true });
  public topK: number = 5;
  public readonly jobStatus = signal('IDLE');
  public readonly uploadedCandidates = signal<UploadedCandidateStatus[]>([]);
  public readonly jobForm = new FormGroup({
    title: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    department: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(120)] }),
    requirements: new FormControl('', { nonNullable: true, validators: [Validators.required] })
  });
  public readonly isSavingJob = signal(false);
  public readonly jobMessage = signal('');
  public readonly createdJob = signal<JobDescriptionResponse | null>(null);
  public readonly candidateForm = new FormGroup({
    fullName: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    email: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.email, Validators.maxLength(254)] })
  });
  public selectedResume: File | null = null;
  public uploadMessage = '';
  public isUploading = false;
  public readonly isSearchingMatches = signal(false);
  public readonly matchMessage = signal('');

  private matchesSubject = new BehaviorSubject<CandidateMatch[]>([]);
  public matches$ = this.matchesSubject.asObservable();

  // Inject JobService instead of HttpClient directly for better separation of concerns
  constructor(private jobService: JobService) {}

  public ngOnInit(): void {
    this.refreshIngestionStatus();
  }

  public onResumeSelected(event: Event): void {
    const input = event.target;
    if (input instanceof HTMLInputElement) {
      this.selectedResume = input.files?.item(0) ?? null;
      this.uploadMessage = '';
    }
  }

  public uploadResume(fileInput: HTMLInputElement): void {
    if (this.candidateForm.invalid || !this.selectedResume || this.isUploading) return;

    this.isUploading = true;
    this.uploadMessage = '';
    const { fullName, email } = this.candidateForm.getRawValue();
    this.jobService.uploadResume(fullName, email, this.selectedResume).subscribe({
      next: (result: ResumeUploadResponse) => {
        this.uploadMessage = `Resume queued for processing (${result.candidateId}).`;
        this.updateUploadedCandidate({
          candidateId: result.candidateId,
          status: result.status,
          errorCode: null,
          statusCheckError: false
        });
        this.pollCandidateStatus(result.candidateId);
        this.candidateForm.reset();
        this.selectedResume = null;
        fileInput.value = '';
      },
      error: () => {
        this.uploadMessage = 'Upload failed. Check the PDF and candidate details, then try again.';
        this.isUploading = false;
      },
      complete: () => this.isUploading = false
    });
  }

  public createJobDescription(): void {
    if (this.jobForm.invalid || this.isSavingJob()) return;

    this.isSavingJob.set(true);
    this.jobMessage.set('');
    const { title, department, requirements } = this.jobForm.getRawValue();
    this.jobService.createJobDescription(title.trim(), department.trim(), requirements.trim()).subscribe({
      next: (job) => {
        this.createdJob.set(job);
        this.jobId.setValue(job.id);
        this.jobMessage.set(`Job description saved: ${job.title}`);
        this.jobForm.reset();
      },
      error: () => {
        this.jobMessage.set('Unable to save this job description. Check the fields and try again.');
        this.isSavingJob.set(false);
      },
      complete: () => this.isSavingJob.set(false)
    });
  }

  public triggerIngestion(): void {
    this.jobStatus.set('RUNNING');
    this.jobService.ingestResumes().subscribe({
        next: () => this.pollIngestionStatus(),
        error: () => {
          this.jobStatus.set('FAILED');
        }
      });
  }

  private refreshIngestionStatus(): void {
    this.jobService.getIngestionStatus().subscribe({
      next: (status) => this.jobStatus.set(status.status),
      error: (error) => console.error('Error fetching ingestion status:', error)
    });
  }

  private pollIngestionStatus(): void {
    timer(0, 1000).pipe(
      switchMap(() => this.jobService.getIngestionStatus()),
      takeWhile((status) => this.isActiveStatus(status.status), true)
    ).subscribe({
      next: (status) => this.jobStatus.set(status.status),
      error: (error) => {
        console.error('Error polling ingestion status:', error);
        this.jobStatus.set('FAILED');
      }
    });
  }

  private isActiveStatus(status: string): boolean {
    return ['STARTING', 'STARTED', 'STOPPING'].includes(status);
  }

  public searchMatches(): void {
    const jobId = this.jobId.value.trim();
    if (!jobId) return;

    this.isSearchingMatches.set(true);
    this.matchMessage.set('');
    this.jobService.getCandidateMatches(jobId, this.topK).subscribe({
      next: (results) => {
        this.matchesSubject.next(results);
        this.matchMessage.set(results.length === 0
          ? 'No candidates met the current similarity threshold. Similarity is a discovery aid, not a hiring decision.'
          : `${results.length} candidate${results.length === 1 ? '' : 's'} found. Review the resume evidence before making a decision.`);
      },
      error: () => {
        this.matchesSubject.next([]);
        this.matchMessage.set('Unable to fetch matches. Check that the backend is running and try again.');
        this.isSearchingMatches.set(false);
      },
      complete: () => this.isSearchingMatches.set(false)
    });
  }

  public getStatusBadgeClass(): string {
    switch (this.jobStatus()) {
      case 'RUNNING':
      case 'STARTING':
      case 'STARTED':
      case 'STOPPING': return 'status-badge--progress';
      case 'COMPLETED': return 'status-badge--complete';
      case 'FAILED': return 'status-badge--failed';
      default: return '';
    }
  }

  public getCandidateStatusBadgeClass(status: string): string {
    switch (status) {
      case 'PENDING':
      case 'PARSED':
      case 'INDEXED': return 'status-badge--progress';
      case 'COMPLETED': return 'status-badge--complete';
      case 'FAILED': return 'status-badge--failed';
      default: return '';
    }
  }

  public retryCandidateStatus(candidateId: string): void {
    this.updateUploadedCandidateById(candidateId, (candidate) => ({
      ...candidate,
      statusCheckError: false
    }));
    this.pollCandidateStatus(candidateId);
  }

  public isJobRunning(): boolean {
    return ['RUNNING', 'STARTING', 'STARTED', 'STOPPING'].includes(this.jobStatus());
  }

  private pollCandidateStatus(candidateId: string): void {
    timer(0, 2000).pipe(
      exhaustMap(() => this.jobService.getCandidateIngestionStatus(candidateId)),
      takeWhile((status) => this.isCandidateProcessing(status.status), true),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (status) => this.updateUploadedCandidate({
        ...status,
        statusCheckError: false
      }),
      error: () => this.updateUploadedCandidateById(candidateId, (candidate) => ({
        ...candidate,
        statusCheckError: true
      }))
    });
  }

  private isCandidateProcessing(status: string): boolean {
    return ['PENDING', 'PARSED', 'INDEXED'].includes(status);
  }

  private updateUploadedCandidate(candidate: UploadedCandidateStatus): void {
    this.uploadedCandidates.update((candidates) => {
      const existingIndex = candidates.findIndex((item) => item.candidateId === candidate.candidateId);
      if (existingIndex === -1) return [candidate, ...candidates];
      return candidates.map((item, index) => index === existingIndex ? candidate : item);
    });
  }

  private updateUploadedCandidateById(
    candidateId: string,
    update: (candidate: UploadedCandidateStatus) => UploadedCandidateStatus
  ): void {
    this.uploadedCandidates.update((candidates) => candidates.map((candidate) =>
      candidate.candidateId === candidateId ? update(candidate) : candidate
    ));
  }
}