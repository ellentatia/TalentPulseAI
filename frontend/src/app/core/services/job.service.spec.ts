import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../environments/environment';
import { JobService } from './job.service';

describe('JobService API contracts', () => {
  let service: JobService;
  let httpTestingController: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(JobService);
    httpTestingController = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTestingController.verify());

  it('uploads a resume as multipart form data', () => {
    const resume = new File(['synthetic resume'], 'resume.pdf', { type: 'application/pdf' });
    let response: unknown;

    service.uploadResume('Test Candidate', 'candidate@example.com', resume).subscribe((result) => {
      response = result;
    });

    const request = httpTestingController.expectOne(`${environment.apiUrl}/candidates/resumes`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    expect((request.request.body as FormData).get('fullName')).toBe('Test Candidate');
    expect((request.request.body as FormData).get('email')).toBe('candidate@example.com');
    expect((request.request.body as FormData).get('file')).toBe(resume);
    request.flush({ candidateId: 'candidate-123', status: 'PENDING' });

    expect(response).toEqual({ candidateId: 'candidate-123', status: 'PENDING' });
  });

  it('fetches candidate processing status from its dedicated endpoint', () => {
    let response: unknown;

    service.getCandidateIngestionStatus('candidate-123').subscribe((result) => {
      response = result;
    });

    const request = httpTestingController.expectOne(
      `${environment.apiUrl}/candidates/candidate-123/ingestion-status`
    );
    expect(request.request.method).toBe('GET');
    request.flush({
      candidateId: 'candidate-123',
      status: 'COMPLETED',
      errorCode: null
    });

    expect(response).toEqual({
      candidateId: 'candidate-123',
      status: 'COMPLETED',
      errorCode: null
    });
  });

  it('creates a job description with the recruiter API contract', () => {
    let response: unknown;

    service.createJobDescription('Medical Assistant', 'Care', 'Patient support').subscribe((result) => {
      response = result;
    });

    const request = httpTestingController.expectOne(`${environment.apiUrl}/jobs`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      title: 'Medical Assistant',
      department: 'Care',
      requirements: 'Patient support'
    });
    request.flush({
      id: 'job-123',
      title: 'Medical Assistant',
      department: 'Care',
      requirements: 'Patient support',
      createdAt: null
    });

    expect(response).toEqual({
      id: 'job-123',
      title: 'Medical Assistant',
      department: 'Care',
      requirements: 'Patient support',
      createdAt: null
    });
  });

  it('sends the requested result count when matching candidates', () => {
    let response: unknown;

    service.getCandidateMatches('job-123', 8).subscribe((result) => {
      response = result;
    });

    const request = httpTestingController.expectOne(
      (candidate) => candidate.url === `${environment.apiUrl}/jobs/job-123/match`
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('topK')).toBe('8');
    request.flush([{
      candidateId: 'candidate-123',
      fullName: 'Synthetic Candidate',
      email: 'candidate@example.com',
      similarityScore: 0.82,
      snippet: 'Skills: patient care',
      matchedSkills: ['patient care'],
      overlappingExperience: 'Profile states 5 years; job description states 3 years.',
      roleFitSummary: 'The profile lists 1 skill also explicitly named in the job description.',
      evidenceSnippets: ['Skills: patient care']
    }]);

    expect(response).toEqual([{
      candidateId: 'candidate-123',
      fullName: 'Synthetic Candidate',
      email: 'candidate@example.com',
      similarityScore: 0.82,
      snippet: 'Skills: patient care',
      matchedSkills: ['patient care'],
      overlappingExperience: 'Profile states 5 years; job description states 3 years.',
      roleFitSummary: 'The profile lists 1 skill also explicitly named in the job description.',
      evidenceSnippets: ['Skills: patient care']
    }]);
  });
});
