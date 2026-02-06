Add-Type -AssemblyName System.Drawing

$baseUrl = "http://localhost:8080"
$password = "password123"

$users = 1..10 | ForEach-Object { "testUser$_" }
$userTokens = @{}

$textStyles = @(
    @{ name = "经典白"; color = "#FFFFFF"; font = "Microsoft YaHei UI"; shadow = $true; shadowColor = "rgba(0,0,0,0.3)" },
    @{ name = "极简黑"; color = "#000000"; font = "Microsoft YaHei UI"; shadow = $false },
    @{ name = "衬线雅"; color = "#FFFFFF"; font = "Times New Roman"; shadow = $true; shadowColor = "rgba(0,0,0,0.3)" },
    @{ name = "活力黄"; color = "#FFD700"; font = "Microsoft YaHei UI"; shadow = $true; shadowColor = "rgba(0,0,0,0.8)" },
    @{ name = "清新绿"; color = "#E0FFEB"; font = "Consolas"; shadow = $true; shadowColor = "#004d00" },
    @{ name = "霓虹粉"; color = "#FF00FF"; font = "Microsoft YaHei UI"; shadow = $true; shadowColor = "#00FFFF"; glow = $true },
    @{ name = "描边黑"; color = "#FFFFFF"; font = "Impact"; stroke = "#000000"; strokeWidth = 3 }
)

$coverStyles = @(
    @{ name = "粉嫩"; type = "gradient"; colors = @("#FF9A9E", "#FECFEF"); decoration = "circles" },
    @{ name = "紫罗兰"; type = "gradient"; colors = @("#a18cd1", "#fbc2eb"); decoration = "circles" },
    @{ name = "清新"; type = "gradient"; colors = @("#84fab0", "#8fd3f4"); decoration = "circles" },
    @{ name = "暗黑"; type = "gradient"; colors = @("#434343", "#000000"); decoration = "grid" },
    @{ name = "日落"; type = "gradient"; colors = @("#fa709a", "#fee140"); decoration = "lines" },
    @{ name = "幽蓝"; type = "gradient"; colors = @("#30cfd0", "#330867"); decoration = "bubbles" },
    @{ name = "纯净白"; type = "solid"; colors = @("#ffffff"); decoration = "border"; defaultTextIndex = 1 },
    @{ name = "复古纸张"; type = "solid"; colors = @("#f4e4bc"); decoration = "noise"; defaultTextIndex = 1 },
    @{ name = "科技蓝"; type = "gradient"; colors = @("#000428", "#004e92"); decoration = "grid" },
    @{ name = "派对"; type = "solid"; colors = @("#FFF5E6"); decoration = "confetti"; defaultTextIndex = 1 },
    @{ name = "几何"; type = "gradient"; colors = @("#2E3192", "#1BFFFF"); decoration = "geometric" },
    @{ name = "赛博"; type = "solid"; colors = @("#000000"); decoration = "neon"; defaultTextIndex = 5 }
)

$tags = @("书籍", "数码", "家居", "玩具", "服装", "文具", "美食", "旅行", "运动", "影音")
$titlePrefix = @("精选", "测评", "分享", "入门", "清单", "指南", "推荐", "体验", "总结", "上手")
$titleSubject = @("心得", "技巧", "搭配", "故事", "避坑", "玩法", "合集", "记录", "对比", "灵感")
$contentOpeners = @(
    "这次想聊聊我的真实体验，整体感受",
    "最近研究了不少资料，发现关键点是",
    "使用一段时间后，我觉得最重要的是",
    "整理了几个实用的小技巧，首先是",
    "从入门到进阶，我的总结是",
    "这里是我对这次体验的简单回顾",
    "如果你正准备尝试，建议先关注",
    "这篇记录了我的实际过程，重点在于"
)
$contentEndings = @(
    "欢迎交流你的看法。",
    "希望能对你有所帮助。",
    "有问题可以留言一起讨论。",
    "如果有更多想法我会继续更新。",
    "以上是我的个人体验，仅供参考。",
    "后续有新发现会再补充。"
)

function Register-User ($username) {
    $body = @{
        username = $username
        password = $password
        email = "$username@example.com"
        nickname = "Nick_$username"
    } | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -Body $body -ContentType "application/json" -ErrorAction SilentlyContinue
    } catch {
    }
}

function Login-User ($username) {
    $body = @{
        username = $username
        password = $password
    } | ConvertTo-Json
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $body -ContentType "application/json"
        return $response.accessToken
    } catch {
        return $null
    }
}

function Parse-Color ($value) {
    if ($value -is [System.Drawing.Color]) { return $value }
    if ($value.StartsWith("#")) {
        $r = [Convert]::ToInt32($value.Substring(1,2),16)
        $g = [Convert]::ToInt32($value.Substring(3,2),16)
        $b = [Convert]::ToInt32($value.Substring(5,2),16)
        return [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
    }
    if ($value.StartsWith("rgba")) {
        $parts = $value.Substring($value.IndexOf("(") + 1).TrimEnd(")").Split(",")
        $r = [int]$parts[0].Trim()
        $g = [int]$parts[1].Trim()
        $b = [int]$parts[2].Trim()
        $a = [double]$parts[3].Trim()
        return [System.Drawing.Color]::FromArgb([int]($a * 255), $r, $g, $b)
    }
    if ($value.StartsWith("rgb")) {
        $parts = $value.Substring($value.IndexOf("(") + 1).TrimEnd(")").Split(",")
        $r = [int]$parts[0].Trim()
        $g = [int]$parts[1].Trim()
        $b = [int]$parts[2].Trim()
        return [System.Drawing.Color]::FromArgb(255, $r, $g, $b)
    }
    if ($value -eq "white") { return [System.Drawing.Color]::White }
    if ($value -eq "black") { return [System.Drawing.Color]::Black }
    return [System.Drawing.Color]::White
}

function Get-Font ($name, $size, $bold = $true) {
    $style = if ($bold) { [System.Drawing.FontStyle]::Bold } else { [System.Drawing.FontStyle]::Regular }
    try { return New-Object System.Drawing.Font($name, $size, $style) } catch { return New-Object System.Drawing.Font("Arial", $size, $style) }
}

function Measure-TextWidth ($graphics, $text, $font) {
    return $graphics.MeasureString($text, $font).Width
}

function New-CoverImageBytes ($text) {
    $width = 600
    $height = 800
    $bgStyle = $coverStyles | Get-Random
    $textIndex = if ($bgStyle.ContainsKey("defaultTextIndex")) { $bgStyle.defaultTextIndex } else { Get-Random -Minimum 0 -Maximum $textStyles.Count }
    $txtStyle = $textStyles[$textIndex]

    $bitmap = New-Object System.Drawing.Bitmap($width, $height)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    if ($bgStyle.type -eq "solid") {
        $brush = New-Object System.Drawing.SolidBrush (Parse-Color $bgStyle.colors[0])
        $graphics.FillRectangle($brush, 0, 0, $width, $height)
        $brush.Dispose()
    } else {
        $c1 = Parse-Color $bgStyle.colors[0]
        $c2 = Parse-Color $bgStyle.colors[1]
        $rect = New-Object System.Drawing.Rectangle(0,0,$width,$height)
        $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 45)
        $graphics.FillRectangle($brush, $rect)
        $brush.Dispose()
    }

    switch ($bgStyle.decoration) {
        "circles" {
            1..50 | ForEach-Object {
                $r = (Get-Random -Minimum 10 -Maximum 50)
                $x = Get-Random -Minimum 0 -Maximum $width
                $y = Get-Random -Minimum 0 -Maximum $height
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(25,255,255,255))
                $graphics.FillEllipse($brush, $x-$r, $y-$r, $r*2, $r*2)
                $brush.Dispose()
            }
        }
        "bubbles" {
            1..30 | ForEach-Object {
                $r = (Get-Random -Minimum 10 -Maximum 80)
                $x = Get-Random -Minimum 0 -Maximum $width
                $y = Get-Random -Minimum 0 -Maximum $height
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(25,255,255,255))
                $graphics.FillEllipse($brush, $x-$r, $y-$r, $r*2, $r*2)
                $brush.Dispose()
            }
        }
        "grid" {
            $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(25,255,255,255), 2)
            for ($x = 0; $x -le $width; $x += 50) { $graphics.DrawLine($pen, $x, 0, $x, $height) }
            for ($y = 0; $y -le $height; $y += 50) { $graphics.DrawLine($pen, 0, $y, $width, $y) }
            $pen.Dispose()
        }
        "lines" {
            $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(40,255,255,255), 3)
            1..20 | ForEach-Object {
                $x = Get-Random -Minimum 0 -Maximum $width
                $y = Get-Random -Minimum 0 -Maximum $height
                $graphics.DrawLine($pen, $x, $y, $x + 200, $y + 200)
            }
            $pen.Dispose()
        }
        "border" {
            $strokeColor = if ($txtStyle.color -eq "#FFFFFF") { [System.Drawing.Color]::FromArgb(204,255,255,255) } else { [System.Drawing.Color]::FromArgb(204,0,0,0) }
            $penWide = New-Object System.Drawing.Pen ($strokeColor, 20)
            $penThin = New-Object System.Drawing.Pen ($strokeColor, 2)
            $graphics.DrawRectangle($penWide, 20, 20, $width - 40, $height - 40)
            $graphics.DrawRectangle($penThin, 50, 50, $width - 100, $height - 100)
            $penWide.Dispose()
            $penThin.Dispose()
        }
        "noise" {
            $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(13,0,0,0))
            1..5000 | ForEach-Object {
                $x = Get-Random -Minimum 0 -Maximum $width
                $y = Get-Random -Minimum 0 -Maximum $height
                $graphics.FillRectangle($brush, $x, $y, 2, 2)
            }
            $brush.Dispose()
        }
        "confetti" {
            $colors = @(
                [System.Drawing.Color]::FromArgb(200,255,199,0),
                [System.Drawing.Color]::FromArgb(200,255,0,0),
                [System.Drawing.Color]::FromArgb(200,46,49,146),
                [System.Drawing.Color]::FromArgb(200,0,158,0),
                [System.Drawing.Color]::FromArgb(200,255,0,255)
            )
            1..100 | ForEach-Object {
                $x = Get-Random -Minimum 0 -Maximum $width
                $y = Get-Random -Minimum 0 -Maximum $height
                $w = Get-Random -Minimum 8 -Maximum 16
                $h = Get-Random -Minimum 4 -Maximum 8
                $brush = New-Object System.Drawing.SolidBrush ($colors | Get-Random)
                $graphics.FillRectangle($brush, $x, $y, $w, $h)
                $brush.Dispose()
            }
        }
        "geometric" {
            1..15 | ForEach-Object {
                $p1 = New-Object System.Drawing.PointF (Get-Random -Minimum 0 -Maximum $width), (Get-Random -Minimum 0 -Maximum $height)
                $p2 = New-Object System.Drawing.PointF (Get-Random -Minimum 0 -Maximum $width), (Get-Random -Minimum 0 -Maximum $height)
                $p3 = New-Object System.Drawing.PointF (Get-Random -Minimum 0 -Maximum $width), (Get-Random -Minimum 0 -Maximum $height)
                $alpha = [int]((0.05 + (Get-Random) * 0.1) * 255)
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb($alpha,255,255,255))
                $graphics.FillPolygon($brush, @($p1,$p2,$p3))
                $brush.Dispose()
            }
        }
        "neon" {
            $penA = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(140,0,255,255), 2)
            $penB = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(140,255,0,255), 2)
            1..5 | ForEach-Object {
                $y = Get-Random -Minimum 0 -Maximum $height
                $graphics.DrawLine($penA, 0, $y, $width, $y)
            }
            1..5 | ForEach-Object {
                $x = Get-Random -Minimum 0 -Maximum $width
                $graphics.DrawLine($penB, $x, 0, $x, $height)
            }
            $penA.Dispose()
            $penB.Dispose()
        }
    }

    $font = Get-Font $txtStyle.font 56 $true
    $textColor = Parse-Color $txtStyle.color
    $shadowColor = Parse-Color ($txtStyle.shadowColor ? $txtStyle.shadowColor : "rgba(0,0,0,0.3)")
    $strokeColor = Parse-Color ($txtStyle.stroke ? $txtStyle.stroke : "#000000")
    $maxWidth = $width - 120
    $lines = New-Object System.Collections.Generic.List[string]
    $line = ""
    foreach ($ch in $text.ToCharArray()) {
        $test = $line + $ch
        if ((Measure-TextWidth $graphics $test $font) -gt $maxWidth -and $line.Length -gt 0) {
            $lines.Add($line)
            $line = $ch
        } else {
            $line = $test
        }
    }
    $lines.Add($line)

    $lineHeight = 70
    $totalHeight = $lines.Count * $lineHeight
    $startY = ($height - $totalHeight) / 2

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $y = $startY + ($i * $lineHeight)
        $lineText = $lines[$i]
        $format = New-Object System.Drawing.StringFormat
        $format.Alignment = [System.Drawing.StringAlignment]::Center
        $format.LineAlignment = [System.Drawing.StringAlignment]::Center
        $layout = New-Object System.Drawing.RectangleF(0, $y, $width, $lineHeight)

        if ($txtStyle.glow) {
            for ($dx = -2; $dx -le 2; $dx++) {
                for ($dy = -2; $dy -le 2; $dy++) {
                    if ($dx -ne 0 -or $dy -ne 0) {
                        $brush = New-Object System.Drawing.SolidBrush $shadowColor
                        $layoutGlow = New-Object System.Drawing.RectangleF($dx, $y + $dy, $width, $lineHeight)
                        $graphics.DrawString($lineText, $font, $brush, $layoutGlow, $format)
                        $brush.Dispose()
                    }
                }
            }
        } elseif ($txtStyle.shadow) {
            $brush = New-Object System.Drawing.SolidBrush $shadowColor
            $layoutShadow = New-Object System.Drawing.RectangleF(2, $y + 2, $width, $lineHeight)
            $graphics.DrawString($lineText, $font, $brush, $layoutShadow, $format)
            $brush.Dispose()
        }

        if ($txtStyle.stroke) {
            for ($dx = -2; $dx -le 2; $dx++) {
                for ($dy = -2; $dy -le 2; $dy++) {
                    if ($dx -ne 0 -or $dy -ne 0) {
                        $brush = New-Object System.Drawing.SolidBrush $strokeColor
                        $layoutStroke = New-Object System.Drawing.RectangleF($dx, $y + $dy, $width, $lineHeight)
                        $graphics.DrawString($lineText, $font, $brush, $layoutStroke, $format)
                        $brush.Dispose()
                    }
                }
            }
        }

        $brush = New-Object System.Drawing.SolidBrush $textColor
        $graphics.DrawString($lineText, $font, $brush, $layout, $format)
        $brush.Dispose()
        $format.Dispose()
    }

    $wmFont = Get-Font "Microsoft YaHei UI" 24 $false
    $wmColor = [System.Drawing.Color]::FromArgb([int](0.6 * 255), $textColor.R, $textColor.G, $textColor.B)
    $wmFormat = New-Object System.Drawing.StringFormat
    $wmFormat.Alignment = [System.Drawing.StringAlignment]::Center
    $wmFormat.LineAlignment = [System.Drawing.StringAlignment]::Center
    $wmRect = New-Object System.Drawing.RectangleF(0, $height - 60, $width, 40)
    $wmBrush = New-Object System.Drawing.SolidBrush $wmColor
    $graphics.DrawString("GoodShare", $wmFont, $wmBrush, $wmRect, $wmFormat)
    $wmBrush.Dispose()
    $wmFormat.Dispose()

    $stream = New-Object System.IO.MemoryStream
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq "image/jpeg" }
    $encoder = New-Object System.Drawing.Imaging.EncoderParameters(1)
    $encoder.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter([System.Drawing.Imaging.Encoder]::Quality, 80L)
    $bitmap.Save($stream, $codec, $encoder)
    $bytes = $stream.ToArray()

    $stream.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    $font.Dispose()
    $wmFont.Dispose()

    return ,$bytes
}

function Upload-Image ($bytes, $token) {
    $client = New-Object System.Net.Http.HttpClient
    $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $token)
    $multipart = New-Object System.Net.Http.MultipartFormDataContent
    $byteContent = New-Object System.Net.Http.ByteArrayContent($bytes)
    $byteContent.Headers.ContentType = New-Object System.Net.Http.Headers.MediaTypeHeaderValue("image/jpeg")
    $multipart.Add($byteContent, "file", "cover.jpg")
    $response = $client.PostAsync("$baseUrl/api/upload", $multipart).Result
    $content = $response.Content.ReadAsStringAsync().Result
    $client.Dispose()
    $multipart.Dispose()
    $byteContent.Dispose()
    if (-not $response.IsSuccessStatusCode) { return $null }
    try { return ($content | ConvertFrom-Json).url } catch { return $null }
}

function New-Title ($tag, $index) {
    return "$tag$($titlePrefix | Get-Random)$($titleSubject | Get-Random) #$("{0:D3}" -f $index)"
}

function New-Content ($tag, $index) {
    return "$($contentOpeners | Get-Random)$tag，整体来说#$("{0:D3}" -f $index) $($contentEndings | Get-Random)"
}

Write-Host "Setting up users..."
foreach ($u in $users) {
    Register-User $u
    $token = Login-User $u
    if ($token) { $userTokens[$u] = $token }
}

if ($userTokens.Count -eq 0) {
    Write-Host "No users available. Exiting."
    exit
}

$targetCount = 100
$created = 0
$attempts = 0
$maxAttempts = $targetCount * 3

Write-Host "Creating posts..."
while ($created -lt $targetCount -and $attempts -lt $maxAttempts) {
    $attempts++
    $tag = $tags | Get-Random
    $title = New-Title $tag ($created + 1)
    $content = New-Content $tag ($created + 1)
    $userKey = $userTokens.Keys | Get-Random
    $token = $userTokens[$userKey]
    $bytes = New-CoverImageBytes $title
    $url = Upload-Image $bytes $token
    if (-not $url) { continue }
    $postBody = @{
        title = $title
        content = $content
        tags = @($tag)
        imageUrls = @($url)
        coverUrl = $url
    } | ConvertTo-Json -Depth 10
    try {
        $response = Invoke-RestMethod -Uri "$baseUrl/api/posts" -Method Post -Body $postBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" }
        $created++
        Write-Host "[OK] $created/$targetCount $title $tag $userKey"
    } catch {
        Write-Host "[FAIL] $($_.Exception.Message)"
    }
    Start-Sleep -Milliseconds 100
}

Write-Host "Batch creation completed. Created: $created"
