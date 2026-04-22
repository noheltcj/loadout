param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RequestedKeys
)

if ($RequestedKeys.Count -gt 0 -and $RequestedKeys[0] -eq "__printenv__") {
    $RequestedKeys = $RequestedKeys | Select-Object -Skip 1
}

foreach ($key in $RequestedKeys) {
    switch ($key.ToUpperInvariant()) {
        "HOME" { $value = $env:HOME; break }
        "XDG_CONFIG_HOME" { $value = $env:XDG_CONFIG_HOME; break }
        "XDG_DATA_HOME" { $value = $env:XDG_DATA_HOME; break }
        "XDG_STATE_HOME" { $value = $env:XDG_STATE_HOME; break }
        "XDG_CACHE_HOME" { $value = $env:XDG_CACHE_HOME; break }
        "PATH" { $value = $env:PATH; break }
        "GIT_DIR" { $value = $env:GIT_DIR; break }
        "GIT_WORK_TREE" { $value = $env:GIT_WORK_TREE; break }
        default { $value = "" }
    }

    [Console]::Out.WriteLine("{0}={1}" -f $key, $value)
}
