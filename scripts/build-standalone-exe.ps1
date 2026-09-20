param()

Add-Type -AssemblyName System.IO.Compression.FileSystem

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"
$payloadZip = Join-Path $basePath "cosmic-payload.zip"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "=== BUILDING STANDALONE ALL-IN-ONE COSMIC CLIENT EXE ===" -ForegroundColor Cyan
Write-Host "=== (Ultra-Minimized Zero-Bloat Single-File Edition) ===" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Ensure Authentic Icon Exists
Write-Host "`n[1/3] Generating authentic classic cosmic.ico..."
& (Join-Path $PSScriptRoot "make-cosmic-ico.ps1")

# 2. Build Offline Payload Archive using Minimal Pipeline
Write-Host "`n[2/3] Packaging embedded ultra-minimized offline vault..."
& python (Join-Path $PSScriptRoot "package-minimal-payload.py")

if (-not (Test-Path $payloadZip)) {
    Write-Host "[ERROR] Failed to generate cosmic-payload.zip!" -ForegroundColor Red
    exit 1
}

$zipSizeMb = (Get-Item $payloadZip).Length / 1MB
Write-Host (" -> Ultra-minimized payload size: {0:0.0} MB" -f $zipSizeMb) -ForegroundColor Green

# 3. Standalone C# Launcher Code with Self-Extractor
$csharpStandalone = @"
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace CosmicClientStandalone
{
    public static class Program
    {
        [DllImport("user32.dll")]
        public static extern bool ReleaseCapture();
        [DllImport("user32.dll")]
        public static extern int SendMessage(IntPtr hWnd, int Msg, int wParam, int lParam);

        public const int WM_NCLBUTTONDOWN = 0xA1;
        public const int HTCAPTION = 0x2;

        public static string BaseDir;
        public static string InstallDir;
        public static string AgentJar;
        public static string ClientJar;
        public static string NativesDir;
        public static string AssetsDir;
        public static string BootstrapJar;
        public static bool Is64BitOS;
        public static Icon AppIcon;

        [STAThread]
        public static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls12;
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            try
            {
                AppIcon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch { }

            BaseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            Is64BitOS = Environment.Is64BitOperatingSystem;

            // 1. Resolve Install Directory: Local directory if files exist, else %APPDATA%\.minecraft\cosmic
            string localJar = Path.Combine(BaseDir, "CosmicClient-x64", "1.8", "CosmicClient-1.8.9.jar");
            string localJarAlt = Path.Combine(BaseDir, "1.8", "CosmicClient-1.8.9.jar");
            string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
            string appDataCosmic = Path.Combine(appData, ".minecraft", "cosmic");
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string localAppDataCosmic = Path.Combine(localAppData, "CosmicClient");

            if (File.Exists(localJar))
            {
                InstallDir = Path.Combine(BaseDir, "CosmicClient-x64");
            }
            else if (File.Exists(localJarAlt))
            {
                InstallDir = BaseDir;
            }
            else if (File.Exists(Path.Combine(appDataCosmic, "1.8", "CosmicClient-1.8.9.jar")))
            {
                InstallDir = appDataCosmic;
            }
            else if (File.Exists(Path.Combine(localAppDataCosmic, "1.8", "CosmicClient-1.8.9.jar")))
            {
                InstallDir = localAppDataCosmic;
            }
            else
            {
                InstallDir = appDataCosmic;
            }

            ClientJar = Path.Combine(InstallDir, "1.8", "CosmicClient-1.8.9.jar");
            NativesDir = Path.Combine(InstallDir, "1.8", "bin-1.8");
            AssetsDir = Path.Combine(InstallDir, "assets_18");
            BootstrapJar = Path.Combine(InstallDir, "Launcher.jar");
            AgentJar = Path.Combine(InstallDir, "cosmic-agent.jar");

            // Parse Command Line Flags
            bool directMode = false;
            string customUser = null;
            int customRam = 0;

            for (int i = 0; i < args.Length; i++)
            {
                string a = args[i].ToLowerInvariant();
                if (a == "--direct" || a == "-d" || a == "/direct") directMode = true;
                if ((a == "--username" || a == "-u" || a == "--user") && i + 1 < args.Length) customUser = args[++i];
                if ((a == "--ram" || a == "-r" || a == "--memory") && i + 1 < args.Length) int.TryParse(args[++i], out customRam);
            }

            // If files are missing, run self-extraction
            if (!File.Exists(ClientJar) || !File.Exists(AgentJar))
            {
                bool extracted = RunSelfExtractionUI();
                if (!extracted) return;
            }

            if (directMode)
            {
                LaunchEngine.LaunchDirect(customUser, customRam);
                return;
            }

            Application.Run(new MainForm(customUser, customRam));
        }

        private static bool RunSelfExtractionUI()
        {
            Assembly asm = Assembly.GetExecutingAssembly();
            string resourceName = null;
            foreach (string n in asm.GetManifestResourceNames())
            {
                if (n.EndsWith("cosmic-payload.zip", StringComparison.OrdinalIgnoreCase))
                {
                    resourceName = n;
                    break;
                }
            }

            if (resourceName == null)
            {
                MessageBox.Show("Embedded payload missing from executable.", "Cosmic Client Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return false;
            }

            Form splash = new Form
            {
                Text = "Cosmic Client - First Run Setup",
                Size = new Size(520, 200),
                StartPosition = FormStartPosition.CenterScreen,
                FormBorderStyle = FormBorderStyle.None,
                BackColor = Color.FromArgb(11, 13, 23),
                ForeColor = Color.White
            };

            if (AppIcon != null) splash.Icon = AppIcon;

            PictureBox pbSplashLogo = new PictureBox
            {
                Size = new Size(32, 32),
                Location = new Point(20, 20),
                SizeMode = PictureBoxSizeMode.Zoom,
                BackColor = Color.Transparent
            };
            if (AppIcon != null) pbSplashLogo.Image = AppIcon.ToBitmap();
            splash.Controls.Add(pbSplashLogo);

            Label lblTitle = new Label
            {
                Text = "Cosmic Client - Setting Up Client Vault...",
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(60, 22),
                AutoSize = true
            };
            splash.Controls.Add(lblTitle);

            Label lblStatus = new Label
            {
                Text = "Setting up pure 1.8.9 client files in " + InstallDir + "...",
                Font = new Font("Segoe UI", 9f),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(60, 56),
                AutoSize = true
            };
            splash.Controls.Add(lblStatus);

            ProgressBar pb = new ProgressBar
            {
                Location = new Point(20, 95),
                Size = new Size(480, 24),
                Style = ProgressBarStyle.Continuous
            };
            splash.Controls.Add(pb);

            Label lblSub = new Label
            {
                Text = "This only runs on first launch. Standalone setup will complete in seconds.",
                Font = new Font("Segoe UI", 8.2f),
                ForeColor = Color.FromArgb(100, 116, 139),
                Location = new Point(20, 135),
                AutoSize = true
            };
            splash.Controls.Add(lblSub);

            bool success = false;

            splash.Shown += (s, e) =>
            {
                Thread t = new Thread(() =>
                {
                    try
                    {
                        Directory.CreateDirectory(InstallDir);
                        using (Stream stream = asm.GetManifestResourceStream(resourceName))
                        {
                            if (stream == null) throw new Exception("Could not open embedded resource stream.");

                            using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                            {
                                int total = archive.Entries.Count;
                                int current = 0;

                                foreach (ZipArchiveEntry entry in archive.Entries)
                                {
                                    current++;
                                    string destinationPath = Path.GetFullPath(Path.Combine(InstallDir, entry.FullName));
                                    
                                    if (string.IsNullOrEmpty(entry.Name))
                                    {
                                        Directory.CreateDirectory(destinationPath);
                                    }
                                    else
                                    {
                                        Directory.CreateDirectory(Path.GetDirectoryName(destinationPath));
                                        entry.ExtractToFile(destinationPath, true);
                                    }

                                    if (current % 15 == 0 || current == total)
                                    {
                                        int pct = (int)((current / (double)total) * 100);
                                        splash.BeginInvoke((Action)(() =>
                                        {
                                            pb.Value = Math.Min(100, Math.Max(0, pct));
                                            lblStatus.Text = string.Format("Extracting files: {0}% ({1}/{2})...", pct, current, total);
                                        }));
                                    }
                                }
                            }
                        }

                        success = true;
                        Thread.Sleep(300);
                        splash.BeginInvoke((Action)(() => splash.Close()));
                    }
                    catch (Exception ex)
                    {
                        splash.BeginInvoke((Action)(() =>
                        {
                            MessageBox.Show("First run extraction failed: " + ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                            splash.Close();
                        }));
                    }
                });
                t.IsBackground = true;
                t.Start();
            };

            splash.ShowDialog();
            return success;
        }
    }

    public static class JavaManager
    {
        public static string ResolveJava(out bool is64BitJava)
        {
            is64BitJava = Program.Is64BitOS;
            string installDir = Program.InstallDir;

            // 1. Bundled Java in Install Directory
            string bundled64 = Path.Combine(installDir, "bootstrap", "java", "bin", "java.exe");
            if (File.Exists(bundled64) && TestJava(bundled64))
            {
                is64BitJava = true;
                return bundled64;
            }

            string bundled32 = Path.Combine(installDir, "bootstrap", "java-x32", "bin", "java.exe");
            if (File.Exists(bundled32) && TestJava(bundled32))
            {
                is64BitJava = false;
                return bundled32;
            }

            // 2. Common System 64-bit JDK Paths
            string[] sys64 = new string[]
            {
                @"C:\Program Files\Java\jdk-21\bin\java.exe",
                @"C:\Program Files\Java\jdk-17\bin\java.exe",
                @"C:\Program Files\Java\jdk-22\bin\java.exe",
                @"C:\Program Files\Java\jdk-24\bin\java.exe",
                @"C:\Program Files\Eclipse Adoptium\jdk-17.0.10.7-hotspot\bin\java.exe",
                @"C:\Program Files\Eclipse Adoptium\jdk-21.0.2.13-hotspot\bin\java.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), @".jdks\corretto-21.0.11\bin\java.exe"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), @".jdks\corretto-17.0.9\bin\java.exe")
            };

            foreach (string p in sys64)
            {
                if (File.Exists(p) && TestJava(p))
                {
                    is64BitJava = true;
                    return p;
                }
            }

            // 3. Common System 32-bit JDK Paths
            string prog86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
            if (!string.IsNullOrEmpty(prog86) && Directory.Exists(Path.Combine(prog86, "Java")))
            {
                foreach (string d in Directory.GetDirectories(Path.Combine(prog86, "Java")))
                {
                    string p = Path.Combine(d, "bin", "java.exe");
                    if (File.Exists(p) && TestJava(p))
                    {
                        is64BitJava = false;
                        return p;
                    }
                }
            }

            // 4. PATH search
            string pathJava = FindInPath("java.exe");
            if (!string.IsNullOrEmpty(pathJava) && TestJava(pathJava))
            {
                is64BitJava = Program.Is64BitOS;
                return pathJava;
            }

            return null;
        }

        public static bool TestJava(string binPath)
        {
            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = binPath,
                    Arguments = "-version",
                    CreateNoWindow = true,
                    UseShellExecute = false,
                    RedirectStandardError = true,
                    RedirectStandardOutput = true
                };
                using (Process p = Process.Start(psi))
                {
                    p.WaitForExit(3000);
                    return p.ExitCode == 0 || p.HasExited;
                }
            }
            catch
            {
                return false;
            }
        }

        public static string FindInPath(string exeName)
        {
            string env = Environment.GetEnvironmentVariable("PATH");
            if (string.IsNullOrEmpty(env)) return null;
            string[] paths = env.Split(';');
            foreach (string p in paths)
            {
                string clean = p.Trim('\"', ' ');
                if (!string.IsNullOrEmpty(clean) && Directory.Exists(clean))
                {
                    string full = Path.Combine(clean, exeName);
                    if (File.Exists(full)) return full;
                }
            }
            return null;
        }

        public static bool DownloadAndExtractZulu(bool for64Bit, Action<int, string> onProgress, out string javaExePath)
        {
            javaExePath = null;
            string installDir = Program.InstallDir;
            string bootstrapDir = Path.Combine(installDir, "bootstrap");
            Directory.CreateDirectory(bootstrapDir);

            string url = for64Bit
                ? "https://cdn.azul.com/zulu/bin/zulu19.32.13-ca-jre19.0.2-win_x64.zip"
                : "https://cdn.azul.com/zulu/bin/zulu19.28.81-ca-jre19.0.0-win_i686.zip";

            string destFolder = for64Bit ? "java" : "java-x32";
            string targetDir = Path.Combine(bootstrapDir, destFolder);
            string zipFile = Path.Combine(bootstrapDir, "temp_jre_" + (for64Bit ? "x64" : "x86") + ".zip");

            try
            {
                onProgress(5, "Connecting to Azul CDN for Zulu 19 JRE (" + (for64Bit ? "64-bit" : "32-bit") + ")...");

                using (WebClient wc = new WebClient())
                {
                    wc.DownloadProgressChanged += (s, e) =>
                    {
                        onProgress(5 + (int)(e.ProgressPercentage * 0.7), string.Format("Downloading Zulu 19 JRE: {0}% ({1:0.0} MB / {2:0.0} MB)...", e.ProgressPercentage, e.BytesReceived / 1048576.0, e.TotalBytesToReceive / 1048576.0));
                    };

                    wc.DownloadFileTaskAsync(new Uri(url), zipFile).GetAwaiter().GetResult();
                }

                onProgress(80, "Download completed. Extracting JRE package...");

                if (Directory.Exists(targetDir))
                {
                    try { Directory.Delete(targetDir, true); } catch { }
                }

                string tempExtract = Path.Combine(bootstrapDir, "extract_" + Path.GetRandomFileName());
                ZipFile.ExtractToDirectory(zipFile, tempExtract);

                string[] subdirs = Directory.GetDirectories(tempExtract);
                if (subdirs.Length > 0)
                {
                    Directory.Move(subdirs[0], targetDir);
                }
                else
                {
                    Directory.Move(tempExtract, targetDir);
                }

                if (Directory.Exists(tempExtract)) Directory.Delete(tempExtract, true);
                if (File.Exists(zipFile)) File.Delete(zipFile);

                string bin = Path.Combine(targetDir, "bin", "java.exe");
                if (File.Exists(bin) && TestJava(bin))
                {
                    javaExePath = bin;
                    onProgress(100, "Java Runtime verified and ready!");
                    return true;
                }

                return false;
            }
            catch (Exception ex)
            {
                onProgress(0, "Error downloading Java: " + ex.Message);
                return false;
            }
        }
    }

    public static class LaunchEngine
    {
        public static Process CurrentGameProcess = null;

        public static void SyncPlayerData()
        {
            try
            {
                string appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
                string cosmicUserDir = Path.Combine(appData, ".minecraft", "cosmic");
                string installDir = Program.InstallDir;

                if (installDir.Equals(cosmicUserDir, StringComparison.OrdinalIgnoreCase)) return;
                if (!Directory.Exists(cosmicUserDir)) return;

                string[] copyDirs = new string[] { "resourcepacks", "shaderpacks", "screenshots", "schematics" };
                foreach (string d in copyDirs)
                {
                    string src = Path.Combine(cosmicUserDir, d);
                    string dst = Path.Combine(installDir, d);
                    if (Directory.Exists(src) && !Directory.Exists(dst))
                    {
                        Directory.CreateDirectory(dst);
                    }
                }
            }
            catch { }
        }

        public static List<string> LoadAccounts()
        {
            List<string> list = new List<string>();
            try
            {
                string accFile = Path.Combine(Program.InstallDir, "accounts.json");
                if (File.Exists(accFile))
                {
                    string json = File.ReadAllText(accFile);
                    int idx = 0;
                    while ((idx = json.IndexOf("\"username\":", idx)) != -1)
                    {
                        int start = json.IndexOf("\"", idx + 11) + 1;
                        int end = json.IndexOf("\"", start);
                        if (start > 0 && end > start)
                        {
                            string u = json.Substring(start, end - start).Trim();
                            if (!string.IsNullOrEmpty(u) && !list.Contains(u)) list.Add(u);
                        }
                        idx = end + 1;
                    }
                }
            }
            catch { }

            if (!list.Contains("CosmicPlayer")) list.Insert(0, "CosmicPlayer");
            return list;
        }

        public static void SaveOfflineAccount(string username)
        {
            try
            {
                string accFile = Path.Combine(Program.InstallDir, "accounts.json");
                string json = "{\n  \"selectedUser\": { \"account\": \"offline_" + username + "\", \"profile\": \"offline_" + username + "\" },\n  \"authenticationDatabase\": {\n    \"offline_" + username + "\": {\n      \"username\": \"" + username + "\",\n      \"accessToken\": \"offline\",\n      \"type\": \"Offline\"\n    }\n  }\n}";
                File.WriteAllText(accFile, json, Encoding.UTF8);
            }
            catch { }
        }

        public static ProcessStartInfo BuildLaunchInfo(string username, int ramMB, string jvmPreset, string resolution, out string summary)
        {
            bool is64BitJava;
            string javaExe = JavaManager.ResolveJava(out is64BitJava);

            if (string.IsNullOrEmpty(javaExe))
            {
                bool ok = JavaManager.DownloadAndExtractZulu(Program.Is64BitOS, (pct, msg) => { }, out javaExe);
                if (!ok || string.IsNullOrEmpty(javaExe))
                {
                    throw new Exception("Unable to locate or automatically download a working Java runtime.");
                }
            }

            if (string.IsNullOrEmpty(username)) username = "CosmicPlayer";

            if (!is64BitJava && ramMB > 1024)
            {
                ramMB = 1024;
            }
            if (ramMB <= 256) ramMB = is64BitJava ? 2048 : 768;

            int maxRam = Math.Max(ramMB, is64BitJava ? 4096 : 1024);

            int width = 1280;
            int height = 720;
            bool fullscreen = false;

            if (resolution.Contains("1920")) { width = 1920; height = 1080; }
            else if (resolution.Contains("2560")) { width = 2560; height = 1440; }
            else if (resolution.Contains("Fullscreen")) { fullscreen = true; }

            string javaHome = Path.GetDirectoryName(Path.GetDirectoryName(javaExe));
            string installDir = Program.InstallDir;

            StringBuilder args = new StringBuilder();

            args.AppendFormat("\"-Dcosmic.java={0}\" ", javaHome);
            args.AppendFormat("\"-Dcosmic.installdir={0}\" ", installDir);
            args.AppendFormat("\"-Dcosmic.bootstrap={0}\" ", Program.BootstrapJar);
            args.Append("-Dcosmic.launcher.load=v1 ");
            args.Append("-Dcosmic.log.plain=true ");
            args.Append("-Dcosmic.version=2.7.0.b84ff ");
            args.Append("-Dcosmic.branch=master ");
            args.Append("-Dapple.awt.application.name=\"Cosmic Client\" ");
            args.Append("-Dapple.awt.application.appearance=system ");
            args.Append("-Duser.language=en-US ");
            args.AppendFormat("\"-Djava.library.path={0}\" ", Program.NativesDir);

            args.AppendFormat("-Xms{0}M -Xmx{1}M -Xmn128M -XX:MaxDirectMemorySize={1}M ", ramMB, maxRam);
            args.Append("-XX:+DisableAttachMechanism -Djdk.attach.allowAttachSelf=false -Dsun.tools.attach.enable=false -Dcosmic.hardening=true -Xshare:off ");
            args.Append("--add-opens java.desktop/java.awt.event=ALL-UNNAMED ");
            args.Append("--add-opens java.desktop/sun.awt=ALL-UNNAMED ");
            args.Append("--add-opens java.management/sun.management=ALL-UNNAMED ");

            args.Append("-Dcosmic.offline=true ");
            if (File.Exists(Program.AgentJar))
            {
                args.AppendFormat("\"-javaagent:{0}\" ", Program.AgentJar);
            }

            if (jvmPreset.Contains("Ultra FPS"))
            {
                args.Append("-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=25 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch ");
            }
            else if (jvmPreset.Contains("Competitive"))
            {
                args.Append("-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=15 -XX:G1ReservePercent=15 -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch ");
            }
            else if (jvmPreset.Contains("Low End"))
            {
                args.Append("-XX:+UseSerialGC -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC ");
            }
            else
            {
                args.Append("-XX:+UseG1GC -XX:MaxGCPauseMillis=50 ");
            }

            args.AppendFormat("-jar \"{0}\" ", Program.ClientJar);

            args.Append("--version 1.8 ");
            args.AppendFormat("--assetsDir \"{0}\" ", Program.AssetsDir);
            args.Append("--assetIndex 1.8 ");
            args.AppendFormat("--username {0} ", username);
            args.Append("--uuid 00000000000000000000000000000000 ");
            args.Append("--accessToken offline ");
            args.Append("--userType msa ");
            args.Append("--versionType CosmicClient ");
            args.Append("--userProperties {} ");
            args.AppendFormat("--width {0} --height {1} ", width, height);
            if (fullscreen) args.Append("--fullscreen ");
            args.Append("--novid");

            summary = string.Format(
                "Java: {0} ({1})\nAgent: {2}\nPlayer: {3}\nMemory: {4} MB\nNatives: {5}",
                javaExe, is64BitJava ? "64-bit" : "32-bit",
                File.Exists(Program.AgentJar) ? "Injected" : "Missing",
                username, ramMB, Program.NativesDir
            );

            ProcessStartInfo psi = new ProcessStartInfo
            {
                FileName = javaExe,
                Arguments = args.ToString(),
                WorkingDirectory = installDir,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true,
                CreateNoWindow = true
            };

            try
            {
                if (psi.EnvironmentVariables.ContainsKey("_JAVA_OPTIONS")) psi.EnvironmentVariables.Remove("_JAVA_OPTIONS");
                if (psi.EnvironmentVariables.ContainsKey("JAVA_TOOL_OPTIONS")) psi.EnvironmentVariables.Remove("JAVA_TOOL_OPTIONS");
                if (psi.EnvironmentVariables.ContainsKey("JAVA_OPTIONS")) psi.EnvironmentVariables.Remove("JAVA_OPTIONS");
                if (psi.EnvironmentVariables.ContainsKey("IBM_JAVA_OPTIONS")) psi.EnvironmentVariables.Remove("IBM_JAVA_OPTIONS");
            }
            catch { }

            return psi;
        }

        public static void LaunchDirect(string customUser, int customRam)
        {
            SyncPlayerData();
            string summary;
            ProcessStartInfo psi = BuildLaunchInfo(customUser ?? "CosmicPlayer", customRam > 0 ? customRam : (Program.Is64BitOS ? 2048 : 768), "Ultra FPS Boost", "1280 x 720", out summary);
            psi.CreateNoWindow = false;
            psi.UseShellExecute = true;
            psi.RedirectStandardOutput = false;
            psi.RedirectStandardError = false;

            Process.Start(psi);
        }
    }

    public class MainForm : Form
    {
        private ComboBox cbAccounts;
        private TextBox txtNewUser;
        private Button btnAddUser;
        private TrackBar tbRam;
        private Label lblRamVal;
        private ComboBox cbPreset;
        private ComboBox cbResolution;
        private Button btnLaunch;
        private Button btnKill;
        private RichTextBox rtbConsole;
        private Label lblSysStatus;
        private Label lblJavaStatus;
        private ProgressBar pbDownload;
        private Label lblDownloadStatus;
        private Panel pnlDownloader;

        private bool is64BitJava = true;
        private string resolvedJavaPath = null;

        public MainForm(string defaultUser, int defaultRam)
        {
            InitializeCustomUI(defaultUser, defaultRam);
            CheckSystem();
        }

        private void InitializeCustomUI(string defaultUser, int defaultRam)
        {
            this.Text = "Cosmic Client Offcloud 1.0 Launcher";
            this.Size = new Size(940, 680);
            this.MinimumSize = new Size(860, 600);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.None;
            this.BackColor = Color.FromArgb(11, 13, 23);
            this.ForeColor = Color.FromArgb(241, 245, 249);

            if (Program.AppIcon != null) this.Icon = Program.AppIcon;

            Panel titleBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 44,
                BackColor = Color.FromArgb(16, 19, 34)
            };
            titleBar.MouseDown += (s, e) =>
            {
                if (e.Button == MouseButtons.Left)
                {
                    Program.ReleaseCapture();
                    Program.SendMessage(this.Handle, Program.WM_NCLBUTTONDOWN, Program.HTCAPTION, 0);
                }
            };

            PictureBox pbLogo = new PictureBox
            {
                Size = new Size(24, 24),
                Location = new Point(14, 10),
                SizeMode = PictureBoxSizeMode.Zoom,
                BackColor = Color.Transparent
            };
            if (Program.AppIcon != null) pbLogo.Image = Program.AppIcon.ToBitmap();
            titleBar.Controls.Add(pbLogo);

            Label lblTitle = new Label
            {
                Text = "COSMIC CLIENT OFFCLOUD 1.0",
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(44, 12),
                AutoSize = true
            };
            titleBar.Controls.Add(lblTitle);

            Button btnClose = new Button
            {
                Text = "X",
                Font = new Font("Segoe UI", 10, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(40, 44),
                Location = new Point(this.Width - 40, 0),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Cursor = Cursors.Hand
            };
            btnClose.FlatAppearance.BorderSize = 0;
            btnClose.FlatAppearance.MouseOverBackColor = Color.FromArgb(225, 29, 72);
            btnClose.Click += (s, e) => Application.Exit();

            Button btnMin = new Button
            {
                Text = "-",
                Font = new Font("Segoe UI", 9, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(40, 44),
                Location = new Point(this.Width - 80, 0),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Cursor = Cursors.Hand
            };
            btnMin.FlatAppearance.BorderSize = 0;
            btnMin.FlatAppearance.MouseOverBackColor = Color.FromArgb(30, 41, 59);
            btnMin.Click += (s, e) => this.WindowState = FormWindowState.Minimized;

            titleBar.Controls.Add(btnClose);
            titleBar.Controls.Add(btnMin);
            this.Controls.Add(titleBar);

            Panel mainContainer = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(20, 15, 20, 15)
            };
            this.Controls.Add(mainContainer);

            Panel bannerPanel = new Panel
            {
                Dock = DockStyle.Top,
                Height = 85,
                BackColor = Color.FromArgb(20, 24, 43)
            };
            bannerPanel.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(124, 58, 237), 1.5f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, bannerPanel.Width - 1, bannerPanel.Height - 1);
                }
            };

            lblSysStatus = new Label
            {
                Text = string.Format("System: {0} Windows | Offcloud Vault: Embedded & Active | Agent: Ready", Program.Is64BitOS ? "64-bit (x64)" : "32-bit (x86)"),
                Font = new Font("Segoe UI", 10f, FontStyle.Bold),
                ForeColor = Color.FromArgb(52, 211, 153),
                Location = new Point(16, 14),
                AutoSize = true
            };
            bannerPanel.Controls.Add(lblSysStatus);

            lblJavaStatus = new Label
            {
                Text = "Java Runtime: Detecting...",
                Font = new Font("Segoe UI", 9f, FontStyle.Regular),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(16, 42),
                AutoSize = true
            };
            bannerPanel.Controls.Add(lblJavaStatus);

            Button btnDownloadJava = new Button
            {
                Text = "Auto-Install Zulu 19 JRE",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = Color.White,
                BackColor = Color.FromArgb(124, 58, 237),
                FlatStyle = FlatStyle.Flat,
                Size = new Size(190, 32),
                Location = new Point(bannerPanel.Width - 210, 25),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Cursor = Cursors.Hand
            };
            btnDownloadJava.FlatAppearance.BorderSize = 0;
            btnDownloadJava.Click += (s, e) => StartJavaDownload();
            bannerPanel.Controls.Add(btnDownloadJava);

            mainContainer.Controls.Add(bannerPanel);

            pnlDownloader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 60,
                BackColor = Color.FromArgb(15, 23, 42),
                Visible = false
            };
            lblDownloadStatus = new Label
            {
                Text = "Starting Java Zulu 19 JRE download...",
                Font = new Font("Segoe UI", 9f),
                ForeColor = Color.FromArgb(56, 189, 248),
                Location = new Point(16, 8),
                AutoSize = true
            };
            pbDownload = new ProgressBar
            {
                Location = new Point(16, 30),
                Size = new Size(bannerPanel.Width - 32, 18),
                Anchor = AnchorStyles.Top | AnchorStyles.Left | AnchorStyles.Right,
                Style = ProgressBarStyle.Continuous
            };
            pnlDownloader.Controls.Add(lblDownloadStatus);
            pnlDownloader.Controls.Add(pbDownload);
            mainContainer.Controls.Add(pnlDownloader);

            TableLayoutPanel splitGrid = new TableLayoutPanel
            {
                Dock = DockStyle.Fill,
                ColumnCount = 2,
                RowCount = 1,
                Padding = new Padding(0, 15, 0, 0)
            };
            splitGrid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 48f));
            splitGrid.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 52f));
            mainContainer.Controls.Add(splitGrid);

            // --- LEFT PANEL ---
            Panel leftPanel = new Panel
            {
                Dock = DockStyle.Fill,
                AutoScroll = true,
                Padding = new Padding(0, 0, 10, 0)
            };
            splitGrid.Controls.Add(leftPanel, 0, 0);

            Label lblAccHeader = new Label
            {
                Text = "PLAYER ACCOUNT (OFFLINE / MSA)",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 5),
                AutoSize = true
            };
            leftPanel.Controls.Add(lblAccHeader);

            cbAccounts = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 10f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(0, 28),
                Size = new Size(240, 30)
            };
            List<string> accounts = LaunchEngine.LoadAccounts();
            foreach (string acc in accounts) cbAccounts.Items.Add(acc);
            if (cbAccounts.Items.Count > 0) cbAccounts.SelectedIndex = 0;
            leftPanel.Controls.Add(cbAccounts);

            txtNewUser = new TextBox
            {
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.FromArgb(203, 213, 225),
                Font = new Font("Segoe UI", 9.5f),
                BorderStyle = BorderStyle.FixedSingle,
                Location = new Point(0, 68),
                Size = new Size(240, 26)
            };
            txtNewUser.Text = "Enter new username...";
            txtNewUser.GotFocus += (s, e) => { if (txtNewUser.Text == "Enter new username...") txtNewUser.Text = ""; };
            leftPanel.Controls.Add(txtNewUser);

            btnAddUser = new Button
            {
                Text = "Add / Select",
                BackColor = Color.FromArgb(39, 45, 75),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                Location = new Point(250, 67),
                Size = new Size(110, 28),
                Cursor = Cursors.Hand
            };
            btnAddUser.FlatAppearance.BorderSize = 0;
            btnAddUser.Click += (s, e) =>
            {
                string u = txtNewUser.Text.Trim();
                if (!string.IsNullOrEmpty(u) && u != "Enter new username...")
                {
                    if (!cbAccounts.Items.Contains(u)) cbAccounts.Items.Add(u);
                    cbAccounts.SelectedItem = u;
                    LaunchEngine.SaveOfflineAccount(u);
                    txtNewUser.Text = "";
                }
            };
            leftPanel.Controls.Add(btnAddUser);

            int maxRamSlider = Program.Is64BitOS ? 8192 : 1024;
            int minRamSlider = 512;
            int initRam = defaultRam > 0 ? defaultRam : (Program.Is64BitOS ? 2048 : 768);
            if (initRam > maxRamSlider) initRam = maxRamSlider;
            if (initRam < minRamSlider) initRam = minRamSlider;

            Label lblRamHeader = new Label
            {
                Text = "RAM ALLOCATION (MEMORY)",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 110),
                AutoSize = true
            };
            leftPanel.Controls.Add(lblRamHeader);

            lblRamVal = new Label
            {
                Text = string.Format("{0} MB ({1:0.0} GB)", initRam, initRam / 1024.0),
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(230, 110),
                AutoSize = true
            };
            leftPanel.Controls.Add(lblRamVal);

            tbRam = new TrackBar
            {
                Minimum = minRamSlider / 256,
                Maximum = maxRamSlider / 256,
                Value = initRam / 256,
                TickFrequency = 2,
                Location = new Point(0, 135),
                Size = new Size(360, 45)
            };
            tbRam.Scroll += (s, e) =>
            {
                int mb = tbRam.Value * 256;
                lblRamVal.Text = string.Format("{0} MB ({1:0.0} GB)", mb, mb / 1024.0);
            };
            leftPanel.Controls.Add(tbRam);

            Label lblPresetHeader = new Label
            {
                Text = "OPTIMIZATION PRESET",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 185),
                AutoSize = true
            };
            leftPanel.Controls.Add(lblPresetHeader);

            cbPreset = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9.5f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(0, 208),
                Size = new Size(360, 28)
            };
            cbPreset.Items.Add("Ultra FPS Boost (Lunar Style - Recommended)");
            cbPreset.Items.Add("Competitive PvP (Low Latency / G1GC)");
            cbPreset.Items.Add("Balanced (Low Memory Garbage Collection)");
            cbPreset.Items.Add("Low End PC (Optimized Heap Footprint)");
            cbPreset.SelectedIndex = 0;
            leftPanel.Controls.Add(cbPreset);

            Label lblResHeader = new Label
            {
                Text = "DISPLAY RESOLUTION",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 248),
                AutoSize = true
            };
            leftPanel.Controls.Add(lblResHeader);

            cbResolution = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9.5f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(0, 271),
                Size = new Size(360, 28)
            };
            cbResolution.Items.Add("1280 x 720 (Standard HD)");
            cbResolution.Items.Add("1920 x 1080 (Full HD)");
            cbResolution.Items.Add("2560 x 1440 (2K QHD)");
            cbResolution.Items.Add("Fullscreen Mode");
            cbResolution.SelectedIndex = 0;
            leftPanel.Controls.Add(cbResolution);

            btnLaunch = new Button
            {
                Text = "LAUNCH COSMIC CLIENT (OFFLINE)",
                Font = new Font("Segoe UI", 11.5f, FontStyle.Bold),
                ForeColor = Color.White,
                BackColor = Color.FromArgb(124, 58, 237),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(0, 318),
                Size = new Size(360, 52),
                Cursor = Cursors.Hand
            };
            btnLaunch.FlatAppearance.BorderSize = 0;
            btnLaunch.Click += (s, e) => LaunchGame();
            leftPanel.Controls.Add(btnLaunch);

            Panel folderButtons = new Panel
            {
                Location = new Point(0, 382),
                Size = new Size(360, 75)
            };

            Button btnPacks = CreateSmallButton("Packs", 0, 0, () => OpenFolder("resourcepacks"));
            Button btnShaders = CreateSmallButton("Shaders", 90, 0, () => OpenFolder("shaderpacks"));
            Button btnScreens = CreateSmallButton("Screenshots", 180, 0, () => OpenFolder("screenshots"));
            Button btnLogs = CreateSmallButton("Logs", 270, 0, () => OpenFolder("logs"));

            Button btnGameDir = CreateSmallButton("Game Dir", 0, 36, () => OpenFolder(""));
            Button btnDirectLaunch = CreateSmallButton("Fast Launch", 135, 36, () => LaunchEngine.LaunchDirect(cbAccounts.SelectedItem as string, tbRam.Value * 256));

            folderButtons.Controls.Add(btnPacks);
            folderButtons.Controls.Add(btnShaders);
            folderButtons.Controls.Add(btnScreens);
            folderButtons.Controls.Add(btnLogs);
            folderButtons.Controls.Add(btnGameDir);
            folderButtons.Controls.Add(btnDirectLaunch);
            leftPanel.Controls.Add(folderButtons);

            // --- RIGHT PANEL ---
            Panel rightPanel = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(10, 0, 0, 0)
            };
            splitGrid.Controls.Add(rightPanel, 1, 0);

            Panel consoleHeader = new Panel
            {
                Dock = DockStyle.Top,
                Height = 30
            };
            Label lblConsoleTitle = new Label
            {
                Text = "LIVE GAME & LAUNCHER CONSOLE",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 6),
                AutoSize = true
            };
            consoleHeader.Controls.Add(lblConsoleTitle);

            btnKill = new Button
            {
                Text = "Kill Process",
                Font = new Font("Segoe UI", 8f, FontStyle.Bold),
                ForeColor = Color.White,
                BackColor = Color.FromArgb(225, 29, 72),
                FlatStyle = FlatStyle.Flat,
                Size = new Size(95, 24),
                Location = new Point(rightPanel.Width - 110, 2),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Visible = false,
                Cursor = Cursors.Hand
            };
            btnKill.FlatAppearance.BorderSize = 0;
            btnKill.Click += (s, e) => KillGame();
            consoleHeader.Controls.Add(btnKill);
            rightPanel.Controls.Add(consoleHeader);

            rtbConsole = new RichTextBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(7, 8, 14),
                ForeColor = Color.FromArgb(226, 232, 240),
                Font = new Font("Consolas", 8.8f),
                BorderStyle = BorderStyle.None,
                ReadOnly = true
            };
            rightPanel.Controls.Add(rtbConsole);

            AppendLog("[Launcher] Cosmic Client Standalone Launcher Ready.", Color.FromArgb(56, 189, 248));
            AppendLog("[Launcher] Architecture: " + (Program.Is64BitOS ? "64-bit (x64)" : "32-bit (x86)") + " Windows", Color.FromArgb(148, 163, 184));
            AppendLog("[Launcher] Vault Location: " + Program.InstallDir, Color.FromArgb(100, 116, 139));
        }

        private Button CreateSmallButton(string text, int x, int y, Action onClick)
        {
            Button b = new Button
            {
                Text = text,
                Font = new Font("Segoe UI", 8.2f, FontStyle.Bold),
                ForeColor = Color.FromArgb(203, 213, 225),
                BackColor = Color.FromArgb(24, 28, 48),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(x, y),
                Size = new Size(82, 30),
                Cursor = Cursors.Hand
            };
            b.FlatAppearance.BorderSize = 0;
            b.Click += (s, e) => onClick();
            return b;
        }

        private void CheckSystem()
        {
            resolvedJavaPath = JavaManager.ResolveJava(out is64BitJava);
            if (!string.IsNullOrEmpty(resolvedJavaPath))
            {
                lblJavaStatus.Text = string.Format("Java: {0} ({1})", resolvedJavaPath, is64BitJava ? "64-bit" : "32-bit");
                lblJavaStatus.ForeColor = Color.FromArgb(52, 211, 153);
                AppendLog("[JavaManager] Detected Java Runtime: " + resolvedJavaPath + " (" + (is64BitJava ? "64-bit" : "32-bit") + ")", Color.FromArgb(52, 211, 153));
            }
            else
            {
                lblJavaStatus.Text = "Java: Missing (Click 'Auto-Install Zulu 19' to download)";
                lblJavaStatus.ForeColor = Color.FromArgb(251, 191, 36);
                AppendLog("[JavaManager] No working Java found. Zulu 19 JRE auto-download is available.", Color.FromArgb(251, 191, 36));
            }
        }

        private void StartJavaDownload()
        {
            pnlDownloader.Visible = true;
            btnLaunch.Enabled = false;

            Thread t = new Thread(() =>
            {
                string downloadedJava;
                bool ok = JavaManager.DownloadAndExtractZulu(Program.Is64BitOS, (pct, msg) =>
                {
                    this.BeginInvoke((Action)(() =>
                    {
                        pbDownload.Value = Math.Min(100, Math.Max(0, pct));
                        lblDownloadStatus.Text = msg;
                        AppendLog("[JavaDownloader] " + msg, Color.FromArgb(56, 189, 248));
                    }));
                }, out downloadedJava);

                this.BeginInvoke((Action)(() =>
                {
                    btnLaunch.Enabled = true;
                    if (ok)
                    {
                        CheckSystem();
                        pnlDownloader.Visible = false;
                        MessageBox.Show("Zulu 19 JRE was successfully installed and configured!", "Java Ready", MessageBoxButtons.OK, MessageBoxIcon.Information);
                    }
                    else
                    {
                        lblDownloadStatus.Text = "Download failed. Check your internet connection.";
                    }
                }));
            });
            t.IsBackground = true;
            t.Start();
        }

        private void LaunchGame()
        {
            if (LaunchEngine.CurrentGameProcess != null && !LaunchEngine.CurrentGameProcess.HasExited)
            {
                MessageBox.Show("Game is already running!", "Cosmic Client", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            try
            {
                LaunchEngine.SyncPlayerData();

                string user = cbAccounts.SelectedItem as string ?? "CosmicPlayer";
                int ram = tbRam.Value * 256;
                string preset = cbPreset.SelectedItem as string ?? "Ultra FPS Boost";
                string res = cbResolution.SelectedItem as string ?? "1280 x 720";

                string summary;
                ProcessStartInfo psi = LaunchEngine.BuildLaunchInfo(user, ram, preset, res, out summary);

                AppendLog("\n=======================================================", Color.FromArgb(124, 58, 237));
                AppendLog("STARTING COSMIC CLIENT (100% OFFCLOUD MODE)", Color.FromArgb(192, 132, 252));
                AppendLog("=======================================================", Color.FromArgb(124, 58, 237));
                AppendLog(summary, Color.FromArgb(203, 213, 225));
                AppendLog("\nExecutable Command:\n" + psi.FileName + " " + psi.Arguments + "\n", Color.FromArgb(100, 116, 139));

                btnLaunch.Text = "RUNNING COSMIC CLIENT...";
                btnLaunch.BackColor = Color.FromArgb(30, 41, 59);
                btnKill.Visible = true;

                Process p = new Process { StartInfo = psi };
                p.OutputDataReceived += (s, e) =>
                {
                    if (e.Data != null)
                    {
                        Color c = Color.FromArgb(226, 232, 240);
                        if (e.Data.Contains("[CosmicAgent]") || e.Data.Contains("Agent")) c = Color.FromArgb(192, 132, 252);
                        else if (e.Data.Contains("ERROR") || e.Data.Contains("Exception") || e.Data.Contains("FATAL")) c = Color.FromArgb(244, 63, 94);
                        else if (e.Data.Contains("WARN")) c = Color.FromArgb(251, 191, 36);
                        AppendLog(e.Data, c);
                    }
                };

                p.ErrorDataReceived += (s, e) =>
                {
                    if (e.Data != null)
                    {
                        Color c = Color.FromArgb(244, 63, 94);
                        if (e.Data.Contains("Picked up _JAVA_OPTIONS")) c = Color.FromArgb(100, 116, 139);
                        AppendLog(e.Data, c);
                    }
                };

                p.EnableRaisingEvents = true;
                p.Exited += (s, e) =>
                {
                    this.BeginInvoke((Action)(() =>
                    {
                        AppendLog(string.Format("\n[Launcher] Game session ended (Exit Code: {0})\n", p.ExitCode), Color.FromArgb(52, 211, 153));
                        btnLaunch.Text = "LAUNCH COSMIC CLIENT (OFFLINE)";
                        btnLaunch.BackColor = Color.FromArgb(124, 58, 237);
                        btnKill.Visible = false;
                        LaunchEngine.CurrentGameProcess = null;
                    }));
                };

                p.Start();
                p.BeginOutputReadLine();
                p.BeginErrorReadLine();
                LaunchEngine.CurrentGameProcess = p;
            }
            catch (Exception ex)
            {
                AppendLog("[Launcher Error] " + ex.Message, Color.FromArgb(244, 63, 94));
                btnLaunch.Text = "LAUNCH COSMIC CLIENT (OFFLINE)";
                btnLaunch.BackColor = Color.FromArgb(124, 58, 237);
                btnKill.Visible = false;
                MessageBox.Show("Launch failed: " + ex.Message, "Cosmic Client Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }

        private void KillGame()
        {
            try
            {
                if (LaunchEngine.CurrentGameProcess != null && !LaunchEngine.CurrentGameProcess.HasExited)
                {
                    LaunchEngine.CurrentGameProcess.Kill();
                    AppendLog("[Launcher] Game process terminated by user.", Color.FromArgb(244, 63, 94));
                }
            }
            catch { }
        }

        private void OpenFolder(string sub)
        {
            try
            {
                string target = string.IsNullOrEmpty(sub) ? Program.InstallDir : Path.Combine(Program.InstallDir, sub);
                if (!Directory.Exists(target)) Directory.CreateDirectory(target);
                Process.Start("explorer.exe", target);
            }
            catch { }
        }

        private void AppendLog(string text, Color color)
        {
            if (this.InvokeRequired)
            {
                this.BeginInvoke((Action)(() => AppendLog(text, color)));
                return;
            }

            rtbConsole.SelectionStart = rtbConsole.TextLength;
            rtbConsole.SelectionLength = 0;
            rtbConsole.SelectionColor = color;
            rtbConsole.AppendText(text + "\n");
            rtbConsole.SelectionColor = rtbConsole.ForeColor;
            rtbConsole.ScrollToCaret();
        }
    }
}
"@

# 4. Compile Standalone All-In-One Executable
Write-Host "`n[3/3] Compiling Cosmic Client Offcloud-1.0 Launcher.exe and standalone binaries..."

$outReleaseLauncher = Join-Path $basePath "Cosmic Client Offcloud-1.0 Launcher.exe"
$outStandalone = Join-Path $basePath "CosmicClient-Standalone.exe"
$outCosmicClient = Join-Path $basePath "CosmicClient.exe"

$provider = New-Object Microsoft.CSharp.CSharpCodeProvider
$params = New-Object System.CodeDom.Compiler.CompilerParameters
$params.GenerateExecutable = $true
$params.OutputAssembly = $outReleaseLauncher
$params.CompilerOptions = "/win32icon:`"$icoPath`" /resource:`"$payloadZip`",cosmic-payload.zip /platform:anycpu /target:winexe /optimize+"

$params.ReferencedAssemblies.Add("System.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.Windows.Forms.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.Drawing.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.IO.Compression.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.IO.Compression.FileSystem.dll") | Out-Null

$res = $provider.CompileAssemblyFromSource($params, $csharpStandalone)
if ($res.Errors.Count -gt 0) {
    Write-Host "[Build Error in Standalone EXE]" -ForegroundColor Red
    $res.Errors | ForEach-Object { Write-Host $_.ErrorText -ForegroundColor Red }
    exit 1
}

# Sync all launcher aliases
Copy-Item $outReleaseLauncher $outStandalone -Force
Copy-Item $outReleaseLauncher $outCosmicClient -Force
$outLauncher = Join-Path $basePath "CosmicClientLauncher.exe"
Copy-Item $outReleaseLauncher $outLauncher -Force
$outOffcloud = Join-Path $basePath "CosmicClientOffcloud.exe"
Copy-Item $outReleaseLauncher $outOffcloud -Force

# Clean up temp payload zip to save disk space
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

# Clean up bloated obsolete installers and temp files as requested by user
$obsoleteFiles = @(
    (Join-Path $basePath "CosmicClient-Installer.exe"),
    (Join-Path $basePath "CosmicClient-Setup.exe"),
    (Join-Path $basePath "installer-payload.zip")
)
foreach ($f in $obsoleteFiles) {
    if (Test-Path $f) {
        Remove-Item $f -Force -ErrorAction SilentlyContinue
        Write-Host " -> Removed obsolete bloated setup file: $(Split-Path $f -Leaf)" -ForegroundColor Yellow
    }
}

$exeSizeMb = (Get-Item $outCosmicClient).Length / 1MB
Write-Host ("`n========================================================") -ForegroundColor Green
Write-Host ("=== [BUILD SUCCESS] ONE-EXE ALL-IN-ONE COSMIC CLIENT ===") -ForegroundColor Green
Write-Host ("=== File: CosmicClient.exe ({0:0.0} MB)" -f $exeSizeMb) -ForegroundColor Green
Write-Host ("=== Authentic Classic Cosmic Client Icon Applied Everywhere ===") -ForegroundColor Green
Write-Host ("========================================================") -ForegroundColor Green
Write-Host "Once downloaded, double-clicking CosmicClient.exe sets up and launches the client automatically with zero bloat!" -ForegroundColor Cyan
