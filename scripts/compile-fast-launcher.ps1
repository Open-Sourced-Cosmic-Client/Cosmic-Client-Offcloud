param()

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"
$launcherOut = Join-Path $basePath "CosmicClientLauncher.exe"
$rootClientOut = Join-Path $basePath "CosmicClient.exe"

$csharpCode = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

namespace CosmicLauncherStub
{
    public static class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            string currentExe = "";
            try { currentExe = Process.GetCurrentProcess().MainModule.FileName; } catch { }

            string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            string knownDir = @"C:\Users\Danny\Downloads\Cosmic Client Testing";

            string[] candidates = new string[]
            {
                Path.Combine(baseDir, "dist", "win-unpacked", "CosmicClient.exe"),
                Path.Combine(knownDir, "dist", "win-unpacked", "CosmicClient.exe"),
                Path.Combine(baseDir, "CosmicClient-x64", "CosmicClient.exe"),
                Path.Combine(knownDir, "CosmicClient-x64", "CosmicClient.exe"),
                Path.Combine(baseDir, "node_modules", "electron", "dist", "electron.exe"),
                Path.Combine(knownDir, "node_modules", "electron", "dist", "electron.exe")
            };

            string targetExe = null;
            string targetArgs = "";
            string workDir = baseDir;

            foreach (string c in candidates)
            {
                if (File.Exists(c) && !string.Equals(c, currentExe, StringComparison.OrdinalIgnoreCase))
                {
                    targetExe = c;
                    if (c.EndsWith("electron.exe", StringComparison.OrdinalIgnoreCase))
                    {
                        string projDir = Directory.Exists(Path.Combine(baseDir, "src")) ? baseDir : knownDir;
                        targetArgs = "\"" + projDir + "\"";
                        workDir = projDir;
                    }
                    else
                    {
                        workDir = Path.GetDirectoryName(c);
                        // If it's dist\win-unpacked, set workDir to project root so all relative paths resolve
                        string grandParent = Path.GetDirectoryName(Path.GetDirectoryName(workDir));
                        if (grandParent != null && Directory.Exists(Path.Combine(grandParent, "CosmicClient-x64")))
                        {
                            workDir = grandParent;
                        }
                    }
                    break;
                }
            }

            if (targetExe != null)
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = targetExe,
                    Arguments = targetArgs,
                    WorkingDirectory = workDir,
                    UseShellExecute = true
                };
                Process.Start(psi);
            }
            else
            {
                MessageBox.Show(
                    "Cosmic Client files not found!\n\nPlease make sure dist\\win-unpacked\\CosmicClient.exe exists or run START-HERE-WINDOWS.bat.",
                    "Cosmic Client",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }
    }
}
'@

$provider = New-Object Microsoft.CSharp.CSharpCodeProvider

function Compile-Stub($outPath) {
    $params = New-Object System.CodeDom.Compiler.CompilerParameters
    $params.GenerateExecutable = $true
    $params.OutputAssembly = $outPath
    $params.CompilerOptions = "/win32icon:`"$icoPath`" /platform:anycpu /target:winexe /optimize+"
    $params.ReferencedAssemblies.Add("System.dll") | Out-Null
    $params.ReferencedAssemblies.Add("System.Windows.Forms.dll") | Out-Null

    $res = $provider.CompileAssemblyFromSource($params, $csharpCode)
    if ($res.Errors.Count -gt 0) {
        Write-Host ("[Build Error for " + $outPath + "]") -ForegroundColor Red
        $res.Errors | ForEach-Object { Write-Host $_.ErrorText -ForegroundColor Red }
        return $false
    }
    Write-Host ("[Fast Launcher] Successfully built: " + $outPath) -ForegroundColor Green
    return $true
}

# 1. Build CosmicClientLauncher.exe
Compile-Stub $launcherOut

# 2. Build root CosmicClient.exe
# Backup old large portable exe if needed
$oldDistPortable = Join-Path $basePath "dist\CosmicClient.exe"
if (Test-Path $rootClientOut) {
    $curLen = (Get-Item $rootClientOut).Length
    if ($curLen -gt 10000000 -and -not (Test-Path $oldDistPortable)) {
        Copy-Item $rootClientOut $oldDistPortable -Force
    }
    Remove-Item $rootClientOut -Force -ErrorAction SilentlyContinue
}
Compile-Stub $rootClientOut
