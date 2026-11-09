[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Runtime.WindowsRuntime
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime]

$asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]

function Await($op, $type) {
    $m = $asTaskGeneric.MakeGenericMethod($type)
    $t = $m.Invoke($null, @($op))
    $t.Wait(-1) | Out-Null
    $t.Result
}

$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])

while ($true) {
    $line = '{"ok":false}'
    try {
        $s = $null
        foreach ($x in $mgr.GetSessions()) {
            if ($x.SourceAppUserModelId -like '*Spotify*') { $s = $x; break }
        }
        if ($s -ne $null) {
            $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
            $tl = $s.GetTimelineProperties()
            $pb = $s.GetPlaybackInfo()
            $obj = @{
                ok      = $true
                title   = [string]$p.Title
                artist  = [string]$p.Artist
                playing = ([int]$pb.PlaybackStatus -eq 4)
                posMs   = [long]$tl.Position.TotalMilliseconds
                durMs   = [long]($tl.EndTime - $tl.StartTime).TotalMilliseconds
                updMs   = [long]$tl.LastUpdatedTime.ToUnixTimeMilliseconds()
            }
            $line = ($obj | ConvertTo-Json -Compress)
        }
    } catch {
        $line = '{"ok":false}'
    }
    [Console]::Out.WriteLine($line)
    [Console]::Out.Flush()
    Start-Sleep -Milliseconds 400
}
