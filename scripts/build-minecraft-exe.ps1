param()

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"
$outputExe = Join-Path $basePath "Minecraft.exe"

if (-not (Test-Path $icoPath)) {
    Write-Host "[Error] cosmic.ico not found at $icoPath" -ForegroundColor Red
    exit 1
}

$csharpCode = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Forms;

[assembly: System.Reflection.AssemblyTitle("Minecraft")]
[assembly: System.Reflection.AssemblyProduct("Cosmic Client Offcloud")]
[assembly: System.Reflection.AssemblyCompany("Cosmic Client")]
[assembly: System.Reflection.AssemblyCopyright("Copyright © 2026")]
[assembly: System.Reflection.AssemblyVersion("1.8.9.0")]
[assembly: System.Reflection.AssemblyFileVersion("1.8.9.0")]

namespace MinecraftRunner
{
    public static class Program
    {
        [DllImport("shell32.dll", SetLastError = true)]
        private static extern int SetCurrentProcessExplicitAppUserModelID([MarshalAs(UnmanagedType.LPWStr)] string AppID);

        [STAThread]
        public static void Main(string[] args)
        {
            try
            {
                SetCurrentProcessExplicitAppUserModelID("Cosmic.Client.1.8.9");
            }
            catch { }

            string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');

            // 1. Resolve Install Directory
            string installDir = Path.Combine(baseDir, "CosmicClient-x64");
            if (!File.Exists(Path.Combine(installDir, "1.8", "CosmicClient-1.8.9.jar")))
            {
                if (File.Exists(Path.Combine(baseDir, "1.8", "CosmicClient-1.8.9.jar")))
                {
                    installDir = baseDir;
                }
                else
                {
                    string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                    string appDataCosmic = Path.Combine(appData, ".minecraft", "cosmic");
                    if (File.Exists(Path.Combine(appDataCosmic, "1.8", "CosmicClient-1.8.9.jar")))
                    {
                        installDir = appDataCosmic;
                    }
                }
            }

            // 2. Resolve Java Executable
            string javaExe = Path.Combine(installDir, "bootstrap", "java", "bin", "javaw.exe");
            if (!File.Exists(javaExe))
                javaExe = Path.Combine(installDir, "bootstrap", "java", "bin", "Minecraft.exe");
            if (!File.Exists(javaExe))
                javaExe = Path.Combine(installDir, "bootstrap", "java", "bin", "java.exe");
            if (!File.Exists(javaExe))
            {
                // Search bootstrap folder
                string bootstrapDir = Path.Combine(installDir, "bootstrap");
                if (Directory.Exists(bootstrapDir))
                {
                    string[] found = Directory.GetFiles(bootstrapDir, "javaw.exe", SearchOption.AllDirectories);
                    if (found.Length > 0) javaExe = found[0];
                    else
                    {
                        found = Directory.GetFiles(bootstrapDir, "java.exe", SearchOption.AllDirectories);
                        if (found.Length > 0) javaExe = found[0];
                    }
                }
            }

            if (string.IsNullOrEmpty(javaExe) || !File.Exists(javaExe))
            {
                javaExe = "javaw.exe";
            }

            // 3. If explicit arguments were supplied, forward them directly
            if (args != null && args.Length > 0)
            {
                StringBuilder sb = new StringBuilder();
                foreach (string a in args)
                {
                    if (a.Contains(" ") && !a.StartsWith("\""))
                        sb.Append("\"").Append(a).Append("\" ");
                    else
                        sb.Append(a).Append(" ");
                }

                LaunchProcess(javaExe, sb.ToString().Trim(), installDir);
                return;
            }

            // 4. Double-clicked: Launch Cosmic Client 1.8.9 direct
            string clientJar = Path.Combine(installDir, "1.8", "CosmicClient-1.8.9.jar");
            string libsDir = Path.Combine(installDir, "1.8", "libraries");
            string nativesDir = Path.Combine(installDir, "1.8", "bin-1.8");
            string assetsDir = Path.Combine(installDir, "assets_18");
            if (!Directory.Exists(assetsDir)) assetsDir = Path.Combine(installDir, "assets");

            string agentJar = Path.Combine(installDir, "cosmic-agent.jar");
            if (!File.Exists(agentJar)) agentJar = Path.Combine(baseDir, "cosmic-agent.jar");

            if (!File.Exists(clientJar))
            {
                MessageBox.Show(
                    "Cosmic Client files not found!\n\nCould not find: " + clientJar + "\nPlease make sure the game files are extracted.",
                    "Cosmic Client - Error",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                return;
            }

            // Build classpath
            List<string> cpList = new List<string>();
            cpList.Add(clientJar);
            if (Directory.Exists(libsDir))
            {
                foreach (string jar in Directory.GetFiles(libsDir, "*.jar", SearchOption.AllDirectories))
                {
                    cpList.Add(jar);
                }
            }
            string classpath = string.Join(";", cpList);

            // Read active account if present
            string username = "CosmicPlayer";
            string uuid = "00000000000000000000000000000000";
            string accFile = Path.Combine(installDir, "cosmic", "accounts.json");
            if (!File.Exists(accFile)) accFile = Path.Combine(installDir, "accounts.json");
            if (File.Exists(accFile))
            {
                try
                {
                    string json = File.ReadAllText(accFile);
                    int uIdx = json.IndexOf("\"username\":");
                    if (uIdx != -1)
                    {
                        int q1 = json.IndexOf("\"", uIdx + 11);
                        int q2 = json.IndexOf("\"", q1 + 1);
                        if (q1 != -1 && q2 != -1)
                            username = json.Substring(q1 + 1, q2 - q1 - 1);
                    }
                    int idIdx = json.IndexOf("\"uuid\":");
                    if (idIdx != -1)
                    {
                        int q1 = json.IndexOf("\"", idIdx + 7);
                        int q2 = json.IndexOf("\"", q1 + 1);
                        if (q1 != -1 && q2 != -1)
                            uuid = json.Substring(q1 + 1, q2 - q1 - 1).Replace("-", "");
                    }
                }
                catch { }
            }

            StringBuilder cmd = new StringBuilder();
            cmd.Append("-Xms2048M -Xmx4096M ");
            cmd.Append("-XX:+UseG1GC -XX:MaxGCPauseMillis=10 -XX:+UnlockExperimentalVMOptions ");
            cmd.Append("-XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:G1HeapRegionSize=32M ");
            cmd.Append("-Djava.library.path=\"").Append(nativesDir).Append("\" ");
            if (File.Exists(agentJar))
            {
                cmd.Append("-javaagent:\"").Append(agentJar).Append("\" ");
            }
            cmd.Append("-cp \"").Append(classpath).Append("\" ");
            cmd.Append("net.minecraft.client.main.Main ");
            cmd.Append("--version 1.8.9 ");
            cmd.Append("--gameDir \"").Append(installDir).Append("\" ");
            cmd.Append("--assetsDir \"").Append(assetsDir).Append("\" ");
            cmd.Append("--assetIndex 1.8 ");
            cmd.Append("--username ").Append(username).Append(" ");
            cmd.Append("--uuid ").Append(uuid).Append(" ");
            cmd.Append("--accessToken \"offline_session\" ");
            cmd.Append("--userType msa ");
            cmd.Append("--versionType CosmicClient ");
            cmd.Append("--userProperties {} ");
            cmd.Append("--width 1280 --height 720 --novid");

            LaunchProcess(javaExe, cmd.ToString(), installDir);
        }

        private static void LaunchProcess(string fileName, string arguments, string workingDir)
        {
            ProcessStartInfo psi = new ProcessStartInfo
            {
                FileName = fileName,
                Arguments = arguments,
                WorkingDirectory = workingDir,
                UseShellExecute = false
            };

            // Clean bad Java options
            psi.EnvironmentVariables.Remove("_JAVA_OPTIONS");
            psi.EnvironmentVariables.Remove("JAVA_TOOL_OPTIONS");
            psi.EnvironmentVariables.Remove("JAVA_OPTIONS");
            psi.EnvironmentVariables.Remove("IBM_JAVA_OPTIONS");

            try
            {
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Failed to launch game:\n" + ex.Message,
                    "Cosmic Client - Launch Error",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }
    }
}
'@

Write-Host "========================================================"
Write-Host "  Compiling Minecraft.exe with Authentic 3-Crystals Icon"
Write-Host "========================================================"

$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$srcTemp = Join-Path $PSScriptRoot "MinecraftRunner.cs"
Set-Content -Path $srcTemp -Value $csharpCode -Encoding UTF8

$destinations = @(
    $outputExe,
    (Join-Path $basePath "CosmicClient-x64\Minecraft.exe"),
    (Join-Path $basePath "Windows\Minecraft.exe"),
    (Join-Path $basePath "Windows-x64\Minecraft.exe"),
    (Join-Path $basePath "dist\win-unpacked\Minecraft.exe")
)

$cmdArgs = @(
    "/nologo",
    "/target:winexe",
    "/platform:anycpu",
    "/optimize+",
    "/win32icon:`"$icoPath`"",
    "/out:`"$outputExe`"",
    "`"$srcTemp`""
)

& $csc $cmdArgs
Remove-Item $srcTemp -Force -ErrorAction SilentlyContinue

if (-not (Test-Path $outputExe)) {
    Write-Host "[ERROR] Failed to compile Minecraft.exe!" -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Successfully compiled $outputExe (Size: $((Get-Item $outputExe).Length) bytes)" -ForegroundColor Green

foreach ($dst in $destinations) {
    if ($dst -ne $outputExe) {
        try {
            $dir = Split-Path $dst
            if (Test-Path $dir) {
                Copy-Item $outputExe $dst -Force
                Write-Host "     Copied to: $dst" -ForegroundColor Cyan
            }
        } catch {}
    }
}

Write-Host "========================================================"
Write-Host "Minecraft.exe ready with authentic 3-crystals icon!" -ForegroundColor Green
Write-Host "========================================================"
