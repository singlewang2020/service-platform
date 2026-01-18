package com.ruler.one.api;

import java.time.format.DateTimeFormatter;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.ruler.one.api.dto.JobDtos;
import com.ruler.one.model.JobDefinition;
import com.ruler.one.service.JobService;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobDtos.JobResponse create(@Valid @RequestBody JobDtos.CreateJobRequest req) {
        JobDefinition job = service.create(req.name(), req.description(), req.type(), req.configJson(), req.enabled());
        return toResponse(job);
    }

    @PutMapping("/{jobId}")
    public JobDtos.JobResponse update(@PathVariable String jobId, @Valid @RequestBody JobDtos.UpdateJobRequest req) {
        JobDefinition job = service.update(jobId, req.name(), req.description(), req.type(), req.configJson(), req.enabled());
        return toResponse(job);
    }

    @DeleteMapping("/{jobId}")
    public void delete(@PathVariable String jobId) {
        boolean ok = service.delete(jobId);
        if (!ok) {
            throw new IllegalArgumentException("job not found: " + jobId);
        }
    }

    @GetMapping("/{jobId}")
    public JobDtos.JobResponse get(@PathVariable String jobId) {
        return toResponse(service.get(jobId));
    }

    @GetMapping
    public JobDtos.PageResponse<JobDtos.JobResponse> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        var r = service.page(page, size, keyword, enabled);
        var items = r.items().stream().map(this::toResponse).toList();
        return new JobDtos.PageResponse<>(r.page(), r.size(), r.total(), items);
    }

    @PostMapping("/{jobId}:enable")
    public JobDtos.JobResponse enable(@PathVariable String jobId) {
        return toResponse(service.enable(jobId));
    }

    @PostMapping("/{jobId}:disable")
    public JobDtos.JobResponse disable(@PathVariable String jobId) {
        return toResponse(service.disable(jobId));
    }

    @PostMapping("/{jobId}:start")
    public JobDtos.StartJobResponse start(@PathVariable String jobId) {
        return new JobDtos.StartJobResponse(service.start(jobId));
    }

    private JobDtos.JobResponse toResponse(JobDefinition job) {
        return new JobDtos.JobResponse(
                job.jobId(),
                job.name(),
                job.description(),
                job.type(),
                job.enabled(),
                job.configJson(),
                job.createdAt() == null ? null : ISO.format(job.createdAt()),
                job.updatedAt() == null ? null : ISO.format(job.updatedAt())
        );
    }
}

