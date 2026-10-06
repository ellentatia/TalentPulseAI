import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { JobService } from '../core/services/job.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent matching feedback', () => {
  let getCandidateMatches: (jobId: string, topK: number) => ReturnType<JobService['getCandidateMatches']>;
  let getCandidateIngestionStatus: (candidateId: string) => ReturnType<JobService['getCandidateIngestionStatus']>;
  let uploadResume: (fullName: string, email: string, file: File) => ReturnType<JobService['uploadResume']>;
  let createJobDescription: (
    title: string,
    department: string,
    requirements: string
  ) => ReturnType<JobService['createJobDescription']>;

  beforeEach(async () => {
    getCandidateMatches = () => of([]);
    getCandidateIngestionStatus = (candidateId) => of({ candidateId, status: 'PENDING', errorCode: null });
    uploadResume = () => of({ candidateId: 'candidate-123', status: 'PENDING' });
    createJobDescription = (title, department, requirements) => of({
      id: 'job-123',
      title,
      department,
      requirements,
      createdAt: null
    });
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        {
          provide: JobService,
          useValue: {
            getIngestionStatus: () => of({ status: 'IDLE' }),
            getCandidateIngestionStatus: (candidateId: string) => getCandidateIngestionStatus(candidateId),
            getCandidateMatches: (jobId: string, topK: number) => getCandidateMatches(jobId, topK),
            uploadResume: (fullName: string, email: string, file: File) => uploadResume(fullName, email, file),
            createJobDescription: (title: string, department: string, requirements: string) =>
              createJobDescription(title, department, requirements)
          },
        },
      ],
    }).compileComponents();
  });

  it('explains when the search returns no candidates', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    fixture.componentInstance.jobId.setValue('job-123');

    fixture.componentInstance.searchMatches();
    fixture.detectChanges();

    expect(fixture.componentInstance.matchMessage()).toContain('No candidates met');
    expect(fixture.nativeElement.textContent).toContain('Similarity is a discovery aid');
  });

  it('shows an error when matching cannot be completed', () => {
    getCandidateMatches = () => throwError(() => new Error('Request failed'));
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    fixture.componentInstance.jobId.setValue('job-123');

    fixture.componentInstance.searchMatches();

    expect(fixture.componentInstance.matchMessage()).toContain('Unable to fetch matches');
    expect(fixture.componentInstance.isSearchingMatches()).toBe(false);
  });

  it('reports upload failure and allows another attempt', () => {
    uploadResume = () => throwError(() => new Error('Request failed'));
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    fixture.componentInstance.candidateForm.setValue({
      fullName: 'Test Candidate',
      email: 'candidate@example.com'
    });
    fixture.componentInstance.selectedResume = new File(['resume'], 'resume.pdf', { type: 'application/pdf' });

    fixture.componentInstance.uploadResume(document.createElement('input'));

    expect(fixture.componentInstance.uploadMessage).toContain('Upload failed');
    expect(fixture.componentInstance.isUploading).toBe(false);
  });

  it('saves a job description and uses its ID to display candidate matches', () => {
    getCandidateMatches = (jobId) => of([{
      candidateId: 'candidate-123',
      fullName: 'Test Candidate',
      email: 'candidate@example.com',
      matchScore: 0.8,
      snippet: 'Relevant synthetic experience'
    }]);
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    fixture.componentInstance.jobForm.setValue({
      title: 'Medical Assistant',
      department: 'Care',
      requirements: 'Patient support'
    });

    fixture.componentInstance.createJobDescription();

    expect(fixture.componentInstance.createdJob()?.id).toBe('job-123');
    expect(fixture.componentInstance.jobId.value).toBe('job-123');
    expect(fixture.componentInstance.jobMessage()).toBe('Job description saved: Medical Assistant');
    expect(fixture.componentInstance.isSavingJob()).toBe(false);

    fixture.componentInstance.searchMatches();
    fixture.detectChanges();

    expect(fixture.componentInstance.matchMessage()).toContain('1 candidate found');
    expect(fixture.nativeElement.textContent).toContain('Test Candidate');
    expect(fixture.nativeElement.textContent).toContain('Relevant synthetic experience');
  });

  it('shows the uploaded candidate status and polls until processing completes', async () => {
    let statusRequests = 0;
    getCandidateIngestionStatus = (candidateId) => {
      statusRequests += 1;
      return of({
        candidateId,
        status: statusRequests === 1 ? 'PENDING' : 'COMPLETED',
        errorCode: null
      });
    };
    vi.useFakeTimers();

    try {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      fixture.componentInstance.candidateForm.setValue({
        fullName: 'Test Candidate',
        email: 'candidate@example.com'
      });
      fixture.componentInstance.selectedResume = new File(['resume'], 'resume.pdf', { type: 'application/pdf' });

      fixture.componentInstance.uploadResume(document.createElement('input'));
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();

      expect(fixture.componentInstance.uploadedCandidates()[0].status).toBe('PENDING');
      await vi.advanceTimersByTimeAsync(2000);
      fixture.detectChanges();

      expect(fixture.componentInstance.uploadedCandidates()).toEqual([{
        candidateId: 'candidate-123',
        status: 'COMPLETED',
        errorCode: null,
        statusCheckError: false
      }]);
      expect(statusRequests).toBe(2);
      expect(fixture.nativeElement.textContent).toContain('COMPLETED');
      expect(fixture.nativeElement.textContent).toContain('separate from the batch job status');
    } finally {
      vi.useRealTimers();
    }
  });

  it('shows the safe error code when candidate processing fails', async () => {
    getCandidateIngestionStatus = (candidateId) =>
      of({ candidateId, status: 'FAILED', errorCode: 'PDF_PARSE_FAILED' });
    vi.useFakeTimers();

    try {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      fixture.componentInstance.candidateForm.setValue({
        fullName: 'Test Candidate',
        email: 'candidate@example.com'
      });
      fixture.componentInstance.selectedResume = new File(['resume'], 'resume.pdf', { type: 'application/pdf' });

      fixture.componentInstance.uploadResume(document.createElement('input'));
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('FAILED');
      expect(fixture.nativeElement.textContent).toContain('PDF_PARSE_FAILED');
    } finally {
      vi.useRealTimers();
    }
  });

  it('offers a retry when candidate status cannot be loaded', async () => {
    let statusRequests = 0;
    getCandidateIngestionStatus = (candidateId) => {
      statusRequests += 1;
      return statusRequests === 1
        ? throwError(() => new Error('Request failed'))
        : of({ candidateId, status: 'COMPLETED', errorCode: null });
    };
    vi.useFakeTimers();

    try {
      const fixture = TestBed.createComponent(DashboardComponent);
      fixture.detectChanges();
      fixture.componentInstance.candidateForm.setValue({
        fullName: 'Test Candidate',
        email: 'candidate@example.com'
      });
      fixture.componentInstance.selectedResume = new File(['resume'], 'resume.pdf', { type: 'application/pdf' });

      fixture.componentInstance.uploadResume(document.createElement('input'));
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Unable to refresh this status.');
      const retryButton = fixture.nativeElement.querySelector('button[type="button"]') as HTMLButtonElement;
      expect(retryButton.textContent).toContain('Retry');
      retryButton.click();
      await vi.advanceTimersByTimeAsync(0);
      fixture.detectChanges();

      expect(statusRequests).toBe(2);
      expect(fixture.nativeElement.textContent).toContain('COMPLETED');
      expect(fixture.nativeElement.textContent).not.toContain('Unable to refresh this status.');
    } finally {
      vi.useRealTimers();
    }
  });
});
