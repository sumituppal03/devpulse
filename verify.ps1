$base = "http://localhost:8080"
$key = (Get-Content "apikey.txt").Trim()
$devId = (Get-Content "devid.txt").Trim()
$repoId = (Get-Content "repoid.txt").Trim()
$headers = @{ Authorization = "Bearer $key" }

Write-Host "=== LIST DEVELOPERS ==="
Invoke-RestMethod -Uri "$base/api/v1/developers" -Headers $headers | Format-List

Write-Host "=== LIST REPOS ==="
Invoke-RestMethod -Uri "$base/api/v1/repos" -Headers $headers | Format-List

Write-Host "=== REPO INDEX STATUS ==="
Invoke-RestMethod -Uri "$base/api/v1/repos/$repoId/index/status" -Headers $headers | Format-List

Write-Host "=== STANDUP HISTORY ==="
Invoke-RestMethod -Uri "$base/api/v1/standup/history?developerId=$devId&days=7" -Headers $headers | Format-List

Write-Host "=== GENERATE STANDUP - no owner/repo params needed ==="
$s = Invoke-RestMethod -Uri "$base/api/v1/standup/generate?developerId=$devId" -Headers $headers
Write-Host "Commit count: $($s.commitCount)"
Write-Host "Summary: $($s.summary)"