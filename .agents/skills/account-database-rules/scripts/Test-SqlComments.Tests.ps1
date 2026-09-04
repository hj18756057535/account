$ErrorActionPreference = 'Stop'
. "$PSScriptRoot/Test-SqlComments.ps1"

$cases = @(
    @{ Name = 'annotated table'; Valid = $true; Columns = 2; Sql = "-- 示例表`ncreate table account_example (`n id varchar(64) primary key, -- 标识`n amount decimal(12, 2) -- 金额`n);" },
    @{ Name = 'missing field comment'; Valid = $false; Sql = "-- 示例表`ncreate table account_example (`n id bigint`n);" },
    @{ Name = 'missing table comment'; Valid = $false; Sql = "create table account_example (`n id bigint -- 标识`n);" },
    @{ Name = 'blank comment'; Valid = $false; Sql = 'alter table account_example add column value text; --' },
    @{ Name = 'English only'; Valid = $false; Sql = 'alter table account_example add column value text; -- value' },
    @{ Name = 'literal is not comment'; Valid = $false; Sql = "alter table account_example add column value text default '-- 不是注释';" },
    @{ Name = 'escaped quote'; Valid = $true; Columns = 1; Sql = "alter table account_example add column value text default 'it''s -- text'; -- 文本" },
    @{ Name = 'default comma'; Valid = $true; Columns = 1; Sql = "-- 示例表`ncreate table account_example (`n value varchar(64) default 'a,b,c' -- 编码`n);" },
    @{ Name = 'inline unsupported'; Valid = $false; Sql = 'create table account_example (id bigint); -- 标识' },
    @{ Name = 'unknown type'; Valid = $false; Sql = "-- 示例表`ncreate table account_example (`n location geometry -- 位置`n);" },
    @{ Name = 'no fields'; Valid = $false; Sql = 'create index idx_example on account_example (id);' },
    @{ Name = 'unclosed'; Valid = $false; Sql = "-- 示例表`ncreate table account_example (`n id bigint -- 标识" }
)
foreach ($case in $cases) {
    $result = Test-SqlCommentText -Sql $case.Sql
    if (($result.Issues.Count -eq 0) -ne $case.Valid) { throw "Unexpected result: $($case.Name)" }
    if ($case.ContainsKey('Columns') -and $result.Columns -ne $case.Columns) { throw "Incorrect coverage: $($case.Name)" }
}
Write-Output "Passed $($cases.Count) SQL comment checker cases."

$postgres = "-- 示例表`ncreate table account_example (`n id bigint -- 标识`n);`ncomment on table account_example is '示例表';`ncomment on column account_example.id is '标识';"
$mysql = "-- 示例表`ncreate table account_example (`n id bigint comment '标识', -- 标识`n payload longtext comment '内容' -- 内容`n) engine=InnoDB default charset=utf8mb4 comment='示例表';"
$metadataCases = @(
    @{ Name = 'PostgreSQL complete'; Valid = $true; Sql = $postgres },
    @{ Name = 'MySQL complete'; Valid = $true; Sql = $mysql },
    @{ Name = 'PostgreSQL missing column'; Valid = $false; Sql = $postgres.Replace("comment on column account_example.id is '标识';", '') },
    @{ Name = 'MySQL missing column'; Valid = $false; Sql = $mysql.Replace("bigint comment '标识'", 'bigint') },
    @{ Name = 'MySQL missing table'; Valid = $false; Sql = $mysql.Replace(") engine=InnoDB default charset=utf8mb4 comment='示例表';", ');') },
    @{ Name = 'wrong COMMENT target'; Valid = $false; Sql = $postgres.Replace('account_example.id is', 'account_example.missing is') },
    @{ Name = 'English metadata'; Valid = $false; Sql = $postgres.Replace("is '标识'", "is 'identifier'") }
)
foreach ($case in $metadataCases) {
    $result = Test-SqlCommentText -Sql $case.Sql -RequireDatabaseComments
    if (($result.Issues.Count -eq 0) -ne $case.Valid) { throw "Unexpected metadata result: $($case.Name): $($result.Issues -join '; ')" }
}
Write-Output "Passed $($metadataCases.Count) database comment checker cases."
