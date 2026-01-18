package com.ruler.one.api;

import java.time.format.DateTimeFormatter;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ruler.one.api.dto.ChainDtos;
import com.ruler.one.api.dto.JobDtos;
import com.ruler.one.model.JobChainDefinition;
import com.ruler.one.service.ChainRunService;
import com.ruler.one.service.ChainService;

@RestController
@RequestMapping("/api/v1/chains")
public class ChainController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ChainService service;
    private final ChainRunService runService;

    public ChainController(ChainService service, ChainRunService runService) {
        this.service = service;
        this.runService = runService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChainDtos.ChainDetailResponse create(@Valid @RequestBody ChainDtos.CreateChainRequest req) {
        JobChainDefinition c = service.create(req.name(), req.description(), req.dagJson(), req.enabled());
        return toDetail(c);
    }

    @PutMapping("/{chainId}")
    public ChainDtos.ChainDetailResponse update(@PathVariable String chainId, @Valid @RequestBody ChainDtos.UpdateChainRequest req) {
        JobChainDefinition c = service.update(chainId, req.name(), req.description(), req.dagJson(), req.version());
        return toDetail(c);
    }

    @PostMapping("/{chainId}:enable")
    public ChainDtos.ChainDetailResponse enable(@PathVariable String chainId) {
        return toDetail(service.enable(chainId));
    }

    @PostMapping("/{chainId}:disable")
    public ChainDtos.ChainDetailResponse disable(@PathVariable String chainId) {
        return toDetail(service.disable(chainId));
    }

    @GetMapping
    public JobDtos.PageResponse<ChainDtos.ChainSummaryResponse> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled
    ) {
        var r = service.page(page, size, keyword, enabled);
        var items = r.items().stream().map(this::toSummary).toList();
        return new JobDtos.PageResponse<>(r.page(), r.size(), r.total(), items);
    }

    @GetMapping("/{chainId}")
    public ChainDtos.ChainDetailResponse detail(@PathVariable String chainId) {
        return toDetail(service.get(chainId));
    }

    @PostMapping("/{chainId}:start")
    public JobDtos.StartJobResponse start(@PathVariable String chainId) {
        String runId = runService.startChain(chainId);
        return new JobDtos.StartJobResponse(runId);
    }

    private ChainDtos.ChainSummaryResponse toSummary(JobChainDefinition c) {
        return new ChainDtos.ChainSummaryResponse(
                c.chainId(),
                c.name(),
                c.description(),
                c.enabled(),
                c.version(),
                c.createdAt() == null ? null : ISO.format(c.createdAt()),
                c.updatedAt() == null ? null : ISO.format(c.updatedAt())
        );
    }

    private ChainDtos.ChainDetailResponse toDetail(JobChainDefinition c) {
        return new ChainDtos.ChainDetailResponse(
                c.chainId(),
                c.name(),
                c.description(),
                c.enabled(),
                c.version(),
                c.dagJson(),
                c.createdAt() == null ? null : ISO.format(c.createdAt()),
                c.updatedAt() == null ? null : ISO.format(c.updatedAt())
        );
    }
}
