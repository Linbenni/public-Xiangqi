[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ExePath
)

$ErrorActionPreference = 'Stop'
$resolvedExePath = (Resolve-Path -LiteralPath $ExePath).Path

Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class LauncherManifestNativeMethods
{
    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr BeginUpdateResource(string fileName, bool deleteExistingResources);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern bool UpdateResource(
        IntPtr updateHandle,
        IntPtr type,
        IntPtr name,
        ushort language,
        byte[] data,
        uint dataSize);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool EndUpdateResource(IntPtr updateHandle, bool discard);
}
'@

$manifest = @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<assembly xmlns="urn:schemas-microsoft-com:asm.v1" manifestVersion="1.0" xmlns:asmv3="urn:schemas-microsoft-com:asm.v3">
  <assemblyIdentity version="1.0.0.0" processorArchitecture="amd64" name="tchess" type="win32" />
  <description>TCHESS</description>
  <dependency>
    <dependentAssembly>
      <assemblyIdentity type="win32" name="Microsoft.Windows.Common-Controls" version="6.0.0.0" processorArchitecture="amd64" publicKeyToken="6595b64144ccf1df" language="*" />
    </dependentAssembly>
  </dependency>
  <trustInfo xmlns="urn:schemas-microsoft-com:asm.v2">
    <security>
      <requestedPrivileges>
        <requestedExecutionLevel level="asInvoker" uiAccess="false" />
      </requestedPrivileges>
    </security>
  </trustInfo>
  <compatibility xmlns="urn:schemas-microsoft-com:compatibility.v1">
    <application>
      <supportedOS Id="{e2011457-1546-43c5-a5fe-008deee3d3f0}" />
      <supportedOS Id="{35138b9a-5d96-4fbd-8e2d-a2440225f93a}" />
      <supportedOS Id="{4a2f28e3-53b9-4441-ba9c-d69d4a4a6e38}" />
      <supportedOS Id="{1f676c76-80e1-4239-95bb-83d0f6d0da78}" />
      <supportedOS Id="{8e0f7a12-bfb3-4fe8-b9a5-48fd50a15a9a}" />
    </application>
  </compatibility>
  <asmv3:application>
    <asmv3:windowsSettings xmlns="http://schemas.microsoft.com/SMI/2005/WindowsSettings">
      <dpiAware>true/PM</dpiAware>
    </asmv3:windowsSettings>
  </asmv3:application>
</assembly>
'@

$manifestBytes = [Text.UTF8Encoding]::new($false).GetBytes($manifest)
$updateHandle = [LauncherManifestNativeMethods]::BeginUpdateResource($resolvedExePath, $false)
if ($updateHandle -eq [IntPtr]::Zero) {
    throw [ComponentModel.Win32Exception]::new([Runtime.InteropServices.Marshal]::GetLastWin32Error())
}

$committed = $false
try {
    $updated = [LauncherManifestNativeMethods]::UpdateResource(
        $updateHandle,
        [IntPtr] 24,
        [IntPtr] 1,
        0,
        $manifestBytes,
        $manifestBytes.Length)
    if (-not $updated) {
        throw [ComponentModel.Win32Exception]::new([Runtime.InteropServices.Marshal]::GetLastWin32Error())
    }
    if (-not [LauncherManifestNativeMethods]::EndUpdateResource($updateHandle, $false)) {
        throw [ComponentModel.Win32Exception]::new([Runtime.InteropServices.Marshal]::GetLastWin32Error())
    }
    $committed = $true
}
finally {
    if (-not $committed) {
        [LauncherManifestNativeMethods]::EndUpdateResource($updateHandle, $true) | Out-Null
    }
}
