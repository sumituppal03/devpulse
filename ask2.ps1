# Read saved values
$key = Get-Content "apikey.txt"
$repoId = Get-Content "repoid.txt"
$devId = Get-Content "devid.txt"

Write-Host "Using key: $key"
Write-Host "Using repo: $repoId"

$base = "http://localhost:8080"

# Ask question 1
$body1 = @{ repositoryId = $repoId.Trim(); question = "What does StandupSummaryService do?" } | ConvertTo-Json
$qa1 = Invoke-RestMethod -Uri "$base/api/v1/codeqa/ask" -Method Post -ContentType "application/json" -Body $body1 -Headers @{ Authorization = "Bearer $($key.Trim())" }
Write-Host "`n=== Q1: StandupSummaryService ==="
Write-Host $qa1.answer
Write-Host "Sources: $($qa1.sourcesUsed -join ', ')"

# Ask question 2
$body2 = @{ repositoryId = $repoId.Trim(); question = "How does RateLimiterService enforce rate limiting with Redis?" } | ConvertTo-Json
$qa2 = Invoke-RestMethod -Uri "$base/api/v1/codeqa/ask" -Method Post -ContentType "application/json" -Body $body2 -Headers @{ Authorization = "Bearer $($key.Trim())" }
Write-Host "`n=== Q2: RateLimiterService ==="
Write-Host $qa2.answer
Write-Host "Sources: $($qa2.sourcesUsed -join ', ')"

# Ask question 3
$body3 = @{ repositoryId = $repoId.Trim(); question = "How does ApiKeyAuthenticationFilter verify a bearer token?" } | ConvertTo-Json
$qa3 = Invoke-RestMethod -Uri "$base/api/v1/codeqa/ask" -Method Post -ContentType "application/json" -Body $body3 -Headers @{ Authorization = "Bearer $($key.Trim())" }
Write-Host "`n=== Q3: ApiKeyAuthenticationFilter ==="
Write-Host $qa3.answer
Write-Host "Sources: $($qa3.sourcesUsed -join ', ')"

# Ask question 4 - exact method name match
$body4 = @{ repositoryId = $repoId.Trim(); question = "What is the summarize method in StandupSummaryService and what parameters does it take?" } | ConvertTo-Json
$qa4 = Invoke-RestMethod -Uri "$base/api/v1/codeqa/ask" -Method Post -ContentType "application/json" -Body $body4 -Headers @{ Authorization = "Bearer $($key.Trim())" }
Write-Host "`n=== Q4: summarize method ==="
Write-Host $qa4.answer
Write-Host "Sources: $($qa4.sourcesUsed -join ', ')"

# Ask question 5 - exact field name
$body5 = @{ repositoryId = $repoId.Trim(); question = "What does the isAllowed method do in RateLimiterService and what key does it use in Redis?" } | ConvertTo-Json
$qa5 = Invoke-RestMethod -Uri "$base/api/v1/codeqa/ask" -Method Post -ContentType "application/json" -Body $body5 -Headers @{ Authorization = "Bearer $($key.Trim())" }
Write-Host "`n=== Q5: isAllowed method ==="
Write-Host $qa5.answer
Write-Host "Sources: $($qa5.sourcesUsed -join ', ')"