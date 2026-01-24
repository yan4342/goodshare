
$baseUrl = "http://localhost:8080"
$password = "password123"

# 1. Setup Users
$users = @("testUser1", "testUser2", "testUser3", "testUser4", "testUser5")
$userTokens = @{}

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
        # Ignore if already exists
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
        Write-Error "Login failed for $username"
        return $null
    }
}

Write-Host "Setting up users..."
foreach ($u in $users) {
    Register-User $u
    $token = Login-User $u
    if ($token) {
        $userTokens[$u] = $token
    }
}

if ($userTokens.Count -eq 0) {
    Write-Error "No users available. Exiting."
    exit
}

# 2. Content Data
$categories = @{
    "书籍" = @(
        @{ title = "读《百年孤独》有感"; content = "这真是一本伟大的魔幻现实主义巨著，马尔克斯的文笔太细腻了。" },
        @{ title = "Java编程思想推荐"; content = "作为Java程序员，这本书是必读的经典，虽然有点厚，但值得一读。" },
        @{ title = "最近看的一本历史书"; content = "明朝那些事儿写得真有趣，把历史写活了。" },
        @{ title = "科幻迷必读三体"; content = "刘慈欣的三体想象力太宏大了，尤其是黑暗森林法则。" }
    )
    "数码" = @(
        @{ title = "iPhone 16 使用体验"; content = "新一代的iPhone在拍照方面提升很大，但是续航似乎没有太多惊喜。" },
        @{ title = "机械键盘入坑指南"; content = "红轴、青轴、茶轴到底怎么选？适合自己的才是最好的。" },
        @{ title = "索尼大法好"; content = "新入手的微单相机，对焦速度真的快，色彩也很讨喜。" },
        @{ title = "MacBook Pro M3 测评"; content = "性能怪兽，剪辑4K视频毫无压力，就是价格稍微有点贵。" }
    )
    "家居" = @(
        @{ title = "极简主义装修风格"; content = "断舍离之后，家里变得宽敞多了，心情也变好了。" },
        @{ title = "宜家好物推荐"; content = "这个收纳盒真的是神器，便宜又好用，推荐给大家。" },
        @{ title = "智能家居改造计划"; content = "把家里的灯光、窗帘都接入了米家，语音控制太方便了。" },
        @{ title = "阳台改造小花园"; content = "种了一些多肉和绿萝，每天看着它们生长，感觉很治愈。" }
    )
    "玩具" = @(
        @{ title = "乐高布加迪拼装记录"; content = "花了整整一个周末才拼好，机械结构太精密了，不得不佩服乐高的设计。" },
        @{ title = "高达模型分享"; content = "MG独角兽，爆甲模式帅炸了，就是贴纸有点多。" },
        @{ title = "Switch游戏推荐"; content = "塞尔达传说荒野之息，真的是开放世界的巅峰之作。" },
        @{ title = "怀旧童年四驱车"; content = "买了一辆旋风冲锋，找回了童年的快乐。" }
    )
    "服装" = @(
        @{ title = "优衣库春夏穿搭"; content = "基础款也能穿出高级感，关键是颜色的搭配。" },
        @{ title = "复古风穿搭分享"; content = "去古着店淘了一件牛仔外套，非常有味道。" },
        @{ title = "运动鞋收藏"; content = "AJ1真的是经典，虽然现在溢价很高，但还是忍不住想买。" },
        @{ title = "职场通勤穿搭"; content = "西装外套配牛仔裤，既正式又不失休闲感。" }
    )
    "文具" = @(
        @{ title = "百乐果汁笔试色"; content = "颜色很正，书写顺滑，做手账必备。" },
        @{ title = "手账入坑第一天"; content = "买了很多胶带和贴纸，希望能坚持记录生活。" },
        @{ title = "凌美钢笔使用感受"; content = "狩猎者系列性价比很高，适合学生党入门。" },
        @{ title = "国誉自我手账本"; content = "时间轴的设计很科学，能够很好地规划每天的时间。" }
    )
    "美食" = @(
        @{ title = "家庭版红烧肉做法"; content = "关键是要炒糖色，还有要小火慢炖，肥而不腻。" },
        @{ title = "探店网红火锅"; content = "排队两小时才吃到，味道确实不错，服务也很好。" },
        @{ title = "自制提拉米苏"; content = "不用烤箱也能做的甜点，手指饼干吸满了咖啡酒，味道很正宗。" },
        @{ title = "深夜食堂泡面法则"; content = "加个荷包蛋，再加根火腿肠，简直是人间美味。" }
    )
}

# 3. Create Posts
Write-Host "Creating posts..."

$rnd = New-Object System.Random

foreach ($tag in $categories.Keys) {
    Write-Host "Processing tag: $tag"
    $items = $categories[$tag]
    
    foreach ($item in $items) {
        # Pick a random user
        $userKey = $users[$rnd.Next(0, $users.Count)]
        $token = $userTokens[$userKey]
        
        $postBody = @{
            title = $item.title
            content = $item.content
            tags = @($tag)
            # No images for pure text posts
            imageUrls = @() 
        } | ConvertTo-Json -Depth 10

        try {
             Invoke-RestMethod -Uri "$baseUrl/api/posts" -Method Post -Body $postBody -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" }
             Write-Host "  [OK] Created post: $($item.title) by $userKey"
        } catch {
             Write-Error "  [FAIL] Failed to create post: $($item.title) - $_"
        }
        
        Start-Sleep -Milliseconds 200
    }
}

Write-Host "Batch creation completed."
