import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment.development';

export interface CandidateMatch {
  candidateId: string;
  fullName: string;
  email: string;
  matchScore: number;
  snippet: string;
}

export interface BatchJobStatus {
  status: string;
  exitDescription: string | null;
  startTime: string | null;
  endTime: string | null;
}

export interface BatchJobLaunchResponse {
  message: string;
}

export interface ResumeUploadResponse {
  candidateId: string;
  status: string;
}

export interface JobDescriptionResponse {
  id: string;
  title: string;
  department: string | null;
  requirements: string;
  createdAt: string | null;
}

@Injectable({
  providedIn: 'root' // Singleton service accessible across the app
})
export class JobService {
// Base URL driven dynamically by the active environment
  private readonly apiUrl = `${environment.apiUrl}/jobs`;
  private readonly recruiterApiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  /**
   * Triggers the Spring Batch resume ingestion job
   */
  public ingestResumes(): Observable<BatchJobLaunchResponse> {
    return this.http.post<BatchJobLaunchResponse>(`${this.apiUrl}/ingest-resumes`, {});
  }

  public getIngestionStatus(): Observable<BatchJobStatus> {
    return this.http.get<BatchJobStatus>(`${this.apiUrl}/ingest-resumes/status`);
  }

  public uploadResume(fullName: string, email: string, file: File): Observable<ResumeUploadResponse> {
    const formData = new FormData();
    formData.append('fullName', fullName);
    formData.append('email', email);
    formData.append('file', file);
    return this.http.post<ResumeUploadResponse>(`${this.recruiterApiUrl}/candidates/resumes`, formData);
  }

  public createJobDescription(
    title: string,
    department: string,
    requirements: string
  ): Observable<JobDescriptionResponse> {
    return this.http.post<JobDescriptionResponse>(`${this.recruiterApiUrl}/jobs`, {
      title,
      department,
      requirements
    });
  }

  /**
   * Fetches top-K candidate matches for a given job ID
   */
  public getCandidateMatches(jobId: string, topK: number = 5): Observable<CandidateMatch[]> {
    const params = new HttpParams().set('topK', topK.toString());
    return this.http.get<CandidateMatch[]>(`${this.apiUrl}/${jobId}/match`, { params });
  }
}