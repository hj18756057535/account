param([string[]]$Path)

# 仅检查 Skill 约定的逐行 CREATE TABLE / ADD COLUMN 源码布局，不执行 SQL。
function Test-SqlCommentText {
    param([Parameter(Mandatory)][string]$Sql)
    $issues = [System.Collections.Generic.List[string]]::new()
    $tables = 0
    $columns = 0
    $inTable = $false
    $previousComment = ''
    $lineNumber = 0
    foreach ($line in ($Sql -split '\r?\n')) {
        $lineNumber++
        $quoted = $false
        $commentAt = -1
        for ($i = 0; $i -lt $line.Length; $i++) {
            if ($line[$i] -eq "'") {
                if ($quoted -and $i + 1 -lt $line.Length -and $line[$i + 1] -eq "'") { $i++; continue }
                $quoted = -not $quoted
            }
            if (-not $quoted -and $i + 1 -lt $line.Length -and $line.Substring($i, 2) -eq '--') {
                $commentAt = $i
                break
            }
        }
        $code = if ($commentAt -ge 0) { $line.Substring(0, $commentAt).Trim() } else { $line.Trim() }
        $comment = if ($commentAt -ge 0) { $line.Substring($commentAt + 2).Trim() } else { '' }
        $hasChinese = $comment -match '[\u3400-\u9fff]'
        if ($quoted) { $issues.Add("${lineNumber}: 不支持跨行字符串，请人工核对") }
        if ($code -match '^create\s+table\s+(?:if\s+not\s+exists\s+)?\w+\s*\($') {
            if ($inTable) { $issues.Add("${lineNumber}: 前一张表未闭合") }
            $tables++
            $inTable = $true
            if ($previousComment -notmatch '[\u3400-\u9fff]') { $issues.Add("${lineNumber}: 缺少紧邻表头的中文表说明") }
        } elseif ($inTable -and $code -eq ');') {
            $inTable = $false
        } elseif ($inTable -and $code) {
            if ($code -match '^(constraint|primary\s+key|foreign\s+key|unique|check)\b') {
                # 约束不计入字段；SQL 语法正确性由数据库定向验证负责。
            } elseif ($code -match '^\w+\s+(varchar\(\d+\)|char\(\d+\)|bigint|integer|int|smallint|boolean|timestamp|date|text|decimal\(\d+,\s*\d+\))(?:\s|,|$)') {
                $columns++
                if (-not $hasChinese) { $issues.Add("${lineNumber}: 字段缺少行末中文说明") }
                if ($code -match '[;]|,\s*\w+\s+\w+') { $issues.Add("${lineNumber}: 字段定义须独占一行，闭合符另起一行") }
            } else { $issues.Add("${lineNumber}: 未识别的字段/约束布局，请人工核对") }
        } elseif ($code -match '^alter\s+table\s+\w+\s+add\s+column\s+\w+\s+.+;$') {
            $columns++
            if (-not $hasChinese) { $issues.Add("${lineNumber}: ADD COLUMN 缺少行末中文说明") }
            if ($code -match ';.+\S|,\s*add\b') { $issues.Add("${lineNumber}: 每条 ADD COLUMN 必须独占一行") }
        } elseif ($code -and $code -notmatch '^create\s+(?:unique\s+)?index\s+.+;$') {
            $issues.Add("${lineNumber}: 不支持的语句/布局，请人工核对，不能视为检查通过")
        }
        $previousComment = if (-not $code) { $comment } else { '' }
    }
    if ($inTable) { $issues.Add('文件结束: 表定义未闭合') }
    if ($columns -eq 0) { $issues.Add('未识别到字段，不能声称字段注释检查通过') }
    [pscustomobject]@{ Tables = $tables; Columns = $columns; Issues = @($issues.ToArray()) }
}

if ($MyInvocation.InvocationName -ne '.') {
    $ErrorActionPreference = 'Stop'
    if (-not $Path) { Write-Error '必须通过 -Path 明确指定本次 SQL 文件'; exit 2 }
    $failed = $false
    foreach ($sqlPath in $Path) {
        $result = Test-SqlCommentText -Sql (Get-Content -LiteralPath $sqlPath -Raw -Encoding UTF8)
        Write-Output "$sqlPath : tables=$($result.Tables), columns=$($result.Columns), issues=$($result.Issues.Count)"
        foreach ($issue in $result.Issues) { Write-Output $issue }
        if ($result.Issues.Count -gt 0) { $failed = $true }
    }
    if ($failed) { exit 1 }
    exit 0
}
