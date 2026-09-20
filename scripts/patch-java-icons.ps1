param()

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"

if (-not (Test-Path $icoPath)) {
    Write-Host "[Error] cosmic.ico not found at $icoPath" -ForegroundColor Red
    exit 1
}

Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Runtime.InteropServices;

public class PeIconUpdater
{
    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Auto)]
    public static extern IntPtr BeginUpdateResource(string pFileName, bool bDeleteExistingResources);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Auto)]
    public static extern bool UpdateResource(IntPtr hUpdate, IntPtr lpType, IntPtr lpName, ushort wLanguage, byte[] lpData, uint cbData);

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool EndUpdateResource(IntPtr hUpdate, bool fDiscard);

    private static readonly IntPtr RT_ICON = (IntPtr)3;
    private static readonly IntPtr RT_GROUP_ICON = (IntPtr)14;

    public static bool InjectIcon(string exePath, string icoPath)
    {
        if (!File.Exists(exePath) || !File.Exists(icoPath))
            return false;

        byte[] icoBytes = File.ReadAllBytes(icoPath);
        if (icoBytes.Length < 6)
            return false;

        ushort reserved = BitConverter.ToUInt16(icoBytes, 0);
        ushort type = BitConverter.ToUInt16(icoBytes, 2);
        ushort count = BitConverter.ToUInt16(icoBytes, 4);

        if (reserved != 0 || type != 1 || count == 0)
            return false;

        IntPtr hUpdate = BeginUpdateResource(exePath, false);
        if (hUpdate == IntPtr.Zero)
            return false;

        try
        {
            byte[] grpData = new byte[6 + count * 14];
            Array.Copy(icoBytes, 0, grpData, 0, 6);

            for (int i = 0; i < count; i++)
            {
                int icoEntryOffset = 6 + i * 16;
                byte width = icoBytes[icoEntryOffset];
                byte height = icoBytes[icoEntryOffset + 1];
                byte colorCount = icoBytes[icoEntryOffset + 2];
                byte res = icoBytes[icoEntryOffset + 3];
                ushort planes = BitConverter.ToUInt16(icoBytes, icoEntryOffset + 4);
                ushort bitCount = BitConverter.ToUInt16(icoBytes, icoEntryOffset + 6);
                uint bytesInRes = BitConverter.ToUInt32(icoBytes, icoEntryOffset + 8);
                uint imageOffset = BitConverter.ToUInt32(icoBytes, icoEntryOffset + 12);

                ushort iconId = (ushort)(i + 1);

                int grpEntryOffset = 6 + i * 14;
                grpData[grpEntryOffset] = width;
                grpData[grpEntryOffset + 1] = height;
                grpData[grpEntryOffset + 2] = colorCount;
                grpData[grpEntryOffset + 3] = res;
                Array.Copy(BitConverter.GetBytes(planes), 0, grpData, grpEntryOffset + 4, 2);
                Array.Copy(BitConverter.GetBytes(bitCount), 0, grpData, grpEntryOffset + 6, 2);
                Array.Copy(BitConverter.GetBytes(bytesInRes), 0, grpData, grpEntryOffset + 8, 4);
                Array.Copy(BitConverter.GetBytes(iconId), 0, grpData, grpEntryOffset + 12, 2);

                byte[] imgBytes = new byte[bytesInRes];
                Array.Copy(icoBytes, imageOffset, imgBytes, 0, bytesInRes);

                if (!UpdateResource(hUpdate, RT_ICON, (IntPtr)iconId, 0, imgBytes, bytesInRes))
                {
                    EndUpdateResource(hUpdate, true);
                    return false;
                }
            }

            if (!UpdateResource(hUpdate, RT_GROUP_ICON, (IntPtr)1, 0, grpData, (uint)grpData.Length))
            {
                EndUpdateResource(hUpdate, true);
                return false;
            }

            return EndUpdateResource(hUpdate, false);
        }
        catch
        {
            EndUpdateResource(hUpdate, true);
            return false;
        }
    }
}
'@

Write-Host "========================================================"
Write-Host "  Injecting Authentic 3-Crystals Icon into Java EXEs"
Write-Host "========================================================"

$targetExes = @(
    (Join-Path $basePath "CosmicClient-x64\bootstrap\java\bin\javaw.exe"),
    (Join-Path $basePath "CosmicClient-x64\bootstrap\java\bin\java.exe"),
    (Join-Path $basePath "CosmicClient-x64\bootstrap\java-x32\bin\javaw.exe"),
    (Join-Path $basePath "CosmicClient-x64\bootstrap\java-x32\bin\java.exe"),
    (Join-Path $basePath "Windows\CosmicClient-x64\bootstrap\java\bin\javaw.exe"),
    (Join-Path $basePath "Windows\CosmicClient-x64\bootstrap\java\bin\java.exe"),
    (Join-Path $basePath "Windows-x64\CosmicClient-x64\bootstrap\java\bin\javaw.exe"),
    (Join-Path $basePath "Windows-x64\CosmicClient-x64\bootstrap\java\bin\java.exe"),
    (Join-Path $basePath "Windows-x32\CosmicClient-x64\bootstrap\java-x32\bin\javaw.exe"),
    (Join-Path $basePath "Windows-x32\CosmicClient-x64\bootstrap\java-x32\bin\java.exe")
)

$appData = [Environment]::GetEnvironmentVariable("APPDATA")
if ($appData) {
    $targetExes += (Join-Path $appData ".minecraft\cosmic\bootstrap\java\bin\javaw.exe")
    $targetExes += (Join-Path $appData ".minecraft\cosmic\bootstrap\java\bin\java.exe")
}

$patchedCount = 0
foreach ($exe in $targetExes) {
    if (Test-Path $exe) {
        $success = [PeIconUpdater]::InjectIcon($exe, $icoPath)
        if ($success) {
            Write-Host "[OK] Injected cosmic.ico into: $exe" -ForegroundColor Green
            $patchedCount++
            
            # Also create Minecraft.exe in the same directory as javaw.exe
            if ($exe.EndsWith("javaw.exe", [StringComparison]::OrdinalIgnoreCase)) {
                $mcInDir = Join-Path (Split-Path $exe) "Minecraft.exe"
                Copy-Item $exe $mcInDir -Force
                Write-Host "     Created companion: $mcInDir" -ForegroundColor Cyan
            }
        } else {
            Write-Host "[WARN] Failed to inject icon into: $exe" -ForegroundColor Yellow
        }
    }
}

Write-Host "========================================================"
Write-Host "Successfully patched $patchedCount Java executables!" -ForegroundColor Green
Write-Host "========================================================"
