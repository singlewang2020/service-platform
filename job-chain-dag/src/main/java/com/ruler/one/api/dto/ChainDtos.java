package com.ruler.one.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public final class ChainDtos {
    private ChainDtos() {}

    public record CreateChainRequest(
            @NotBlank String name,
            String description,
            @NotBlank String dagJson,
            Boolean enabled
    ) {}

    public record UpdateChainRequest(
            @NotBlank String name,
            String description,
            @NotBlank String dagJson,
            @Min(1) long version
    ) {}

    public record ChainSummaryResponse(
            String chainId,
            String name,
            String description,
            boolean enabled,
            long version,
            String createdAt,
            String updatedAt
    ) {}

    public record ChainDetailResponse(
            String chainId,
            String name,
            String description,
            boolean enabled,
            long version,
            String dagJson,
            String createdAt,
            String updatedAt
    ) {}
}
