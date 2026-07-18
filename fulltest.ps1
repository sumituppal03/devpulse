$base = "http://localhost:8080"

# 1. Register tenant
$t = Invoke-RestMethod -Uri "$base/api/v1/tenants/register" -Method Post -ContentType "application/json" -Body '{"name":"AcmeCorp"}'
$key = $t.apiKey
Write-Host "API Key: $key"

# 2. Register developer
$d = Invoke-RestMethod -Uri "$base/api/v1/developers" -Method Post -ContentType "application/json" -Body '{"githubUsername":"sumituppal03"}' -Headers @{ Authorization = "Bearer $key" }
Write-Host "Developer ID: $($d.developerId)"

# 3. Register repo
$r = Invoke-RestMethod -Uri "$base/api/v1/repos" -Method Post -ContentType "application/json" -Body '{"githubOwner":"sumituppal03","githubRepo":"devpulse","defaultBranch":"main"}' -Headers @{ Authorization = "Bearer $key" }
Write-Host "Repo ID: $($r.repositoryId)"

# Save to files
$key | Out-File "apikey.txt"
$d.developerId | Out-File "devid.txt"
$r.repositoryId | Out-File "repoid.txt"

Write-Host "Done - run verify.ps1 to test all endpoints"