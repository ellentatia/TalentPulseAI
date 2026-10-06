import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';
import { JobService } from '../core/services/job.service';
import { DashboardComponent } from './dashboard.component';

describe('DashboardComponent matching feedback', () => {
  let getCandidateMatches: (jobId: string, topK: number) => ReturnType<JobService['getCandidateMatches']>;

  beforeEach(async () => {
    getCandidateMatches = () => of([]);
    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        {
          provide: JobService,
          useValue: {
            getIngestionStatus: () => of({ status: 'IDLE' }),
            getCandidateMatches: (jobId: string, topK: number) => getCandidateMatches(jobId, topK),
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
});
