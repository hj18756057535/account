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
