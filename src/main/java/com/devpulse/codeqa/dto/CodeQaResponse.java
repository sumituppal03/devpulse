package com.devpulse.codeqa.dto;

import java.util.List;

public record CodeQaResponse(
        String answer,
        List<String> sourcesUsed,
        int chunksRetrieved,
        boolean groundedInCode
) {}