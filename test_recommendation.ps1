
$baseUrl = "http://localhost:8080"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$userA = "userA_$timestamp"
$userB = "userB_$timestamp"
$password = "password123"

function Register-User ($username) {
    $body = @{
        username = $username
        password = $password
        email = "$username@example.com"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -Body $body -ContentType "application/json"
}

function Login-User ($username) {
    $body = @{
        username = $username
        password = $password
    } | ConvertTo-Json
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
        Write-Host "Login successful for $username"
        return $response.accessToken
    } catch {
        Write-Error "Login failed for $username : $_"
        return $null
    }
}

Write-Host "1. Registering Users..."
try {
    Register-User $userA
    Register-User $userB
} catch {
    Write-Warning "Registration might have failed or users already exist. Continuing..."
}

Write-Host "2. Logging in..."
$tokenA = Login-User $userA
$tokenB = Login-User $userB

if (-not $tokenA -or -not $tokenB) {
    Write-Error "Failed to get tokens. Exiting."
    exit
}

Write-Host "Token A acquired."
Write-Host "Token B acquired."

$headersA = @{ Authorization = "Bearer $tokenA" }
$headersB = @{ Authorization = "Bearer $tokenB" }

Write-Host "3. Fetching Posts..."
# Get latest posts
$posts = Invoke-RestMethod -Uri "$baseUrl/api/posts" -Method Get
if ($posts.Count -lt 2) {
    Write-Error "Need at least 2 posts to test recommendation."
    exit
}
$post1 = $posts[0].id
$post2 = $posts[1].id
Write-Host "Using Post 1 ID: $post1"
Write-Host "Using Post 2 ID: $post2"

Write-Host "4. Simulating Interactions..."
# User A likes Post 1 and Post 2
Invoke-RestMethod -Uri "$baseUrl/api/posts/$post1/likes" -Method Post -Headers $headersA
Invoke-RestMethod -Uri "$baseUrl/api/posts/$post2/likes" -Method Post -Headers $headersA

# User B likes Post 1
Invoke-RestMethod -Uri "$baseUrl/api/posts/$post1/likes" -Method Post -Headers $headersB

Write-Host "5. Getting Recommendations for User B..."
# Get User B ID
$userInfoB = Invoke-RestMethod -Uri "$baseUrl/api/profile" -Method Get -Headers $headersB
$userIdB = $userInfoB.id

$recommendations = Invoke-RestMethod -Uri "$baseUrl/api/recommendations?user_id=$userIdB" -Method Get -Headers $headersB

Write-Host "--- Recommendations Result ---"
$recommendations | Format-Table id, title

$found = $false
foreach ($rec in $recommendations) {
    if ($rec.id -eq $post2) {
        $found = $true
        break
    }
}

if ($found) {
    Write-Host "SUCCESS: Post $post2 was recommended to User B!" -ForegroundColor Green
} else {
    Write-Host "FAILURE: Post $post2 was NOT recommended." -ForegroundColor Red
}
