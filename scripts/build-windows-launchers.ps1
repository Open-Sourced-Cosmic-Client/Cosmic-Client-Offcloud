param()

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"

# Ensure ultra-HD vector icon exists
if (-not (Test-Path $icoPath)) {
    Write-Host "[Build] Generating Ultra-HD cosmic.ico..."
    & (Join-Path $PSScriptRoot "make-hd-cosmic-ico.ps1")
}

$csharpLauncher = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace CosmicClientLauncher
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

        [STAThread]
        public static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls12;
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            BaseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            string tempPath = Path.GetTempPath().TrimEnd('\\', '/');
            Is64BitOS = Environment.Is64BitOperatingSystem;

            // 1. Resolve Install Directory
            if (File.Exists(Path.Combine(BaseDir, "CosmicClient-x64", "1.8", "CosmicClient-1.8.9.jar")))
            {
                InstallDir = Path.Combine(BaseDir, "CosmicClient-x64");
            }
            else if (File.Exists(Path.Combine(BaseDir, "1.8", "CosmicClient-1.8.9.jar")))
            {
                InstallDir = BaseDir;
            }
            else if (File.Exists(Path.Combine(BaseDir, "..", "CosmicClient-x64", "1.8", "CosmicClient-1.8.9.jar")))
            {
                InstallDir = Path.GetFullPath(Path.Combine(BaseDir, "..", "CosmicClient-x64"));
            }
            else
            {
                InstallDir = Path.Combine(BaseDir, "CosmicClient-x64");
            }

            ClientJar = Path.Combine(InstallDir, "1.8", "CosmicClient-1.8.9.jar");
            NativesDir = Path.Combine(InstallDir, "1.8", "bin-1.8");
            AssetsDir = Path.Combine(InstallDir, "assets_18");
            BootstrapJar = Path.Combine(InstallDir, "Launcher.jar");

            // Resolve Agent JAR
            if (File.Exists(Path.Combine(BaseDir, "cosmic-agent.jar")))
                AgentJar = Path.Combine(BaseDir, "cosmic-agent.jar");
            else if (File.Exists(Path.Combine(InstallDir, "cosmic-agent.jar")))
                AgentJar = Path.Combine(InstallDir, "cosmic-agent.jar");
            else
                AgentJar = Path.Combine(BaseDir, "cosmic-agent.jar");

            // Extraction check
            if (BaseDir.StartsWith(tempPath, StringComparison.OrdinalIgnoreCase) || !File.Exists(ClientJar))
            {
                MessageBox.Show(
                    "Cosmic Client files must be extracted before running!\n\n" +
                    "1. Close this window.\n" +
                    "2. Right-click the downloaded ZIP archive and select 'Extract All...'.\n" +
                    "3. Open the extracted folder and run CosmicClientLauncher.exe or START-HERE-WINDOWS.bat!",
                    "Cosmic Client - Extraction Required",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning
                );
                return;
            }

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

            if (directMode)
            {
                LaunchEngine.LaunchDirect(customUser, customRam);
                return;
            }

            Application.Run(new MainForm(customUser, customRam));
        }

        public static void DrawVectorLogo(Graphics g, float cx, float cy, float size)
        {
            float scale = size / 256.0f;
            var state = g.Save();
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;

            // 1. Subtle Outer Glow
            float glowRad = 118.0f * scale;
            using (GraphicsPath pathGlow = new GraphicsPath())
            {
                pathGlow.AddEllipse(cx - glowRad, cy - glowRad, glowRad * 2, glowRad * 2);
                using (PathGradientBrush pgb = new PathGradientBrush(pathGlow))
                {
                    pgb.CenterColor = Color.FromArgb(50, 139, 92, 246);
                    pgb.SurroundColors = new Color[] { Color.FromArgb(0, 11, 13, 23) };
                    g.FillPath(pgb, pathGlow);
                }
            }

            // 2. Back Half of Orbital Ring
            float ringWidth = 220.0f * scale;
            float ringHeight = 72.0f * scale;
            float ringThickness = Math.Max(2.5f, 14.0f * scale);

            var stateBeforeRing = g.Save();
            g.TranslateTransform(cx, cy);
            g.RotateTransform(-28.0f);
            g.SetClip(new RectangleF(-ringWidth, -ringHeight, ringWidth * 2, ringHeight));
            using (Pen penBack = new Pen(Color.FromArgb(180, 236, 72, 153), ringThickness))
            {
                g.DrawEllipse(penBack, -ringWidth / 2.0f, -ringHeight / 2.0f, ringWidth, ringHeight);
            }
            g.Restore(stateBeforeRing);

            // 3. Main Planet Body
            float planetRad = 74.0f * scale;
            RectangleF planetRect = new RectangleF(cx - planetRad, cy - planetRad, planetRad * 2, planetRad * 2);
            using (LinearGradientBrush brushPlanet = new LinearGradientBrush(
                new PointF(cx - planetRad, cy - planetRad),
                new PointF(cx + planetRad, cy + planetRad),
                Color.FromArgb(255, 109, 40, 217),
                Color.FromArgb(255, 6, 182, 212)))
            {
                g.FillEllipse(brushPlanet, planetRect);
            }
            using (Pen penPlanet = new Pen(Color.FromArgb(220, 192, 132, 252), Math.Max(1.0f, 3.5f * scale)))
            {
                g.DrawEllipse(penPlanet, planetRect);
            }

            // 4. Front Half of Orbital Ring
            var stateFrontRing = g.Save();
            g.TranslateTransform(cx, cy);
            g.RotateTransform(-28.0f);
            g.SetClip(new RectangleF(-ringWidth, 0, ringWidth * 2, ringHeight));
            using (LinearGradientBrush brushRing = new LinearGradientBrush(
                new PointF(-ringWidth / 2.0f, 0),
                new PointF(ringWidth / 2.0f, 0),
                Color.FromArgb(255, 236, 72, 153),
                Color.FromArgb(255, 139, 92, 246)))
            {
                using (Pen penFront = new Pen(brushRing, ringThickness))
                {
                    g.DrawEllipse(penFront, -ringWidth / 2.0f, -ringHeight / 2.0f, ringWidth, ringHeight);
                }
            }
            using (Pen penInner = new Pen(Color.FromArgb(160, 255, 255, 255), Math.Max(0.8f, 2.2f * scale)))
            {
                g.DrawEllipse(penInner, -ringWidth / 2.0f, -ringHeight / 2.0f, ringWidth, ringHeight);
            }
            g.Restore(stateFrontRing);

            // 5. Central Glowing Core
            float coreRad = 26.0f * scale;
            RectangleF coreRect = new RectangleF(cx - coreRad, cy - coreRad, coreRad * 2, coreRad * 2);
            using (LinearGradientBrush brushCore = new LinearGradientBrush(
                new PointF(cx - coreRad, cy - coreRad),
                new PointF(cx + coreRad, cy + coreRad),
                Color.FromArgb(255, 243, 232, 255),
                Color.FromArgb(255, 168, 85, 247)))
            {
                g.FillEllipse(brushCore, coreRect);
            }
            using (Pen penCore = new Pen(Color.FromArgb(240, 255, 255, 255), Math.Max(1.0f, 2.0f * scale)))
            {
                g.DrawEllipse(penCore, coreRect);
            }

            // 6. Star sparkles for larger icons
            if (size >= 48)
            {
                using (SolidBrush brushStar = new SolidBrush(Color.FromArgb(230, 255, 255, 255)))
                {
                    g.FillEllipse(brushStar, cx + 70.0f * scale, cy - 75.0f * scale, 5.0f * scale, 5.0f * scale);
                    g.FillEllipse(brushStar, cx - 85.0f * scale, cy + 60.0f * scale, 4.0f * scale, 4.0f * scale);
                }
            }

            g.Restore(state);
        }
    }

    public static class JavaManager
    {
        public static string ResolveJava(out bool is64BitJava)
        {
            is64BitJava = Program.Is64BitOS;
            string installDir = Program.InstallDir;

            // 1. Bundled Java
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
        private Panel pnlPlay;
        private Panel pnlSettings;
        private Panel pnlLogs;

        private Button btnTabPlay;
        private Button btnTabSettings;
        private Button btnTabLogs;

        private ComboBox cbAccounts;
        private TextBox txtNewUser;
        private Button btnAddUser;
        private TrackBar tbRam;
        private Label lblRamVal;
        private ComboBox cbPreset;
        private ComboBox cbResolution;

        private Button btnLaunch;
        private Button btnKill;
        private Label lblLaunchSub;
        private RichTextBox rtbConsole;
        private Label lblSysStatus;
        private Label lblJavaStatus;

        private bool is64BitJava = true;
        private string resolvedJavaPath = null;

        public MainForm(string defaultUser, int defaultRam)
        {
            InitializeCustomUI(defaultUser, defaultRam);
            CheckSystem();
        }

        private void InitializeCustomUI(string defaultUser, int defaultRam)
        {
            this.Text = "Cosmic Client Offcloud";
            this.Size = new Size(940, 620);
            this.MinimumSize = new Size(880, 560);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.None;
            this.BackColor = Color.FromArgb(11, 13, 23); // #0B0D17 Deep Space Obsidian
            this.ForeColor = Color.FromArgb(241, 245, 249);

            try
            {
                string ico = Path.Combine(Program.BaseDir, "cosmic.ico");
                if (File.Exists(ico)) this.Icon = new Icon(ico);
            }
            catch { }

            // 1. Sleek Modern Lunar-Style TitleBar with Vector Logo & Tabs
            Panel titleBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 52,
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
            titleBar.Paint += (s, e) =>
            {
                // Draw vector Cosmic logo
                Program.DrawVectorLogo(e.Graphics, 26, 26, 30);
                using (Pen borderPen = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawLine(borderPen, 0, 51, titleBar.Width, 51);
                }
            };

            Label lblTitle = new Label
            {
                Text = "COSMIC CLIENT",
                Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(50, 15),
                AutoSize = true
            };
            titleBar.Controls.Add(lblTitle);

            Label lblTag = new Label
            {
                Text = "OFFCLOUD 1.8.9",
                Font = new Font("Segoe UI", 8.2f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(175, 17),
                AutoSize = true
            };
            titleBar.Controls.Add(lblTag);

            // Nav Tabs in Titlebar (Lunar Client Style)
            btnTabPlay = CreateNavTab("PLAY", 330, true, () => SwitchTab(pnlPlay, btnTabPlay));
            btnTabSettings = CreateNavTab("SETTINGS", 410, false, () => SwitchTab(pnlSettings, btnTabSettings));
            btnTabLogs = CreateNavTab("LOGS", 515, false, () => SwitchTab(pnlLogs, btnTabLogs));

            titleBar.Controls.Add(btnTabPlay);
            titleBar.Controls.Add(btnTabSettings);
            titleBar.Controls.Add(btnTabLogs);

            // Window Action Buttons
            Button btnClose = new Button
            {
                Text = "X",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(44, 52),
                Location = new Point(this.Width - 44, 0),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Cursor = Cursors.Hand
            };
            btnClose.FlatAppearance.BorderSize = 0;
            btnClose.FlatAppearance.MouseOverBackColor = Color.FromArgb(225, 29, 72);
            btnClose.Click += (s, e) => Application.Exit();

            Button btnMin = new Button
            {
                Text = "-",
                Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(44, 52),
                Location = new Point(this.Width - 88, 0),
                Anchor = AnchorStyles.Top | AnchorStyles.Right,
                Cursor = Cursors.Hand
            };
            btnMin.FlatAppearance.BorderSize = 0;
            btnMin.FlatAppearance.MouseOverBackColor = Color.FromArgb(30, 41, 59);
            btnMin.Click += (s, e) => this.WindowState = FormWindowState.Minimized;

            titleBar.Controls.Add(btnClose);
            titleBar.Controls.Add(btnMin);
            this.Controls.Add(titleBar);

            // 2. Main Content Host Container
            Panel hostContainer = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(24, 20, 24, 20)
            };
            this.Controls.Add(hostContainer);

            BuildPlayTab(hostContainer, defaultUser, defaultRam);
            BuildSettingsTab(hostContainer, defaultRam);
            BuildLogsTab(hostContainer);

            SwitchTab(pnlPlay, btnTabPlay);
        }

        private Button CreateNavTab(string text, int x, bool active, Action onClick)
        {
            Button b = new Button
            {
                Text = text,
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = active ? Color.FromArgb(192, 132, 252) : Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Location = new Point(x, 0),
                Size = new Size(115, 52),
                Cursor = Cursors.Hand
            };
            b.FlatAppearance.BorderSize = 0;
            b.Click += (s, e) => onClick();
            return b;
        }

        private void SwitchTab(Panel target, Button activeBtn)
        {
            pnlPlay.Visible = (target == pnlPlay);
            pnlSettings.Visible = (target == pnlSettings);
            pnlLogs.Visible = (target == pnlLogs);

            btnTabPlay.ForeColor = (activeBtn == btnTabPlay) ? Color.FromArgb(192, 132, 252) : Color.FromArgb(148, 163, 184);
            btnTabSettings.ForeColor = (activeBtn == btnTabSettings) ? Color.FromArgb(192, 132, 252) : Color.FromArgb(148, 163, 184);
            btnTabLogs.ForeColor = (activeBtn == btnTabLogs) ? Color.FromArgb(192, 132, 252) : Color.FromArgb(148, 163, 184);
        }

        private void BuildPlayTab(Panel host, string defaultUser, int defaultRam)
        {
            pnlPlay = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent
            };
            host.Controls.Add(pnlPlay);

            // Hero Banner Card
            Panel heroCard = new Panel
            {
                Dock = DockStyle.Top,
                Height = 180,
                BackColor = Color.FromArgb(18, 22, 38)
            };
            heroCard.Paint += (s, e) =>
            {
                using (Pen borderPen = new Pen(Color.FromArgb(124, 58, 237), 1.5f))
                {
                    e.Graphics.DrawRectangle(borderPen, 0, 0, heroCard.Width - 1, heroCard.Height - 1);
                }
                Program.DrawVectorLogo(e.Graphics, 80, 90, 110);
            };

            Label lblHeroTitle = new Label
            {
                Text = "COSMIC CLIENT 1.8.9",
                Font = new Font("Segoe UI", 16f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(155, 30),
                AutoSize = true
            };
            heroCard.Controls.Add(lblHeroTitle);

            Label lblHeroDesc = new Label
            {
                Text = "Official Offcloud Minecraft PvP Edition | Bytecode Agent Injected | Ultra FPS Engine",
                Font = new Font("Segoe UI", 9.5f),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(157, 65),
                AutoSize = true
            };
            heroCard.Controls.Add(lblHeroDesc);

            lblSysStatus = new Label
            {
                Text = string.Format("System: {0} Windows   |   Vault: 100% Offline   |   Agent: Ready", Program.Is64BitOS ? "64-bit (x64)" : "32-bit (x86)"),
                Font = new Font("Segoe UI", 8.8f, FontStyle.Bold),
                ForeColor = Color.FromArgb(52, 211, 153),
                Location = new Point(157, 100),
                AutoSize = true
            };
            heroCard.Controls.Add(lblSysStatus);

            lblJavaStatus = new Label
            {
                Text = "Detecting Java Runtime...",
                Font = new Font("Segoe UI", 8.5f),
                ForeColor = Color.FromArgb(56, 189, 248),
                Location = new Point(157, 125),
                AutoSize = true
            };
            heroCard.Controls.Add(lblJavaStatus);

            pnlPlay.Controls.Add(heroCard);

            // Center Launch Section (Lunar Client Style Giant Button)
            Panel launchSection = new Panel
            {
                Dock = DockStyle.Top,
                Height = 140,
                Padding = new Padding(0, 20, 0, 0)
            };

            btnLaunch = new Button
            {
                Text = "LAUNCH COSMIC CLIENT 1.8.9",
                Font = new Font("Segoe UI", 12.5f, FontStyle.Bold),
                ForeColor = Color.White,
                BackColor = Color.FromArgb(124, 58, 237),
                FlatStyle = FlatStyle.Flat,
                Size = new Size(540, 58),
                Location = new Point((892 - 540) / 2, 25),
                Anchor = AnchorStyles.Top,
                Cursor = Cursors.Hand
            };
            btnLaunch.FlatAppearance.BorderSize = 0;
            btnLaunch.FlatAppearance.MouseOverBackColor = Color.FromArgb(147, 51, 234);
            btnLaunch.Click += (s, e) => LaunchGame();
            launchSection.Controls.Add(btnLaunch);

            btnKill = new Button
            {
                Text = "STOP GAME",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(244, 63, 94),
                BackColor = Color.FromArgb(30, 41, 59),
                FlatStyle = FlatStyle.Flat,
                Size = new Size(120, 32),
                Location = new Point((892 - 120) / 2, 92),
                Anchor = AnchorStyles.Top,
                Visible = false,
                Cursor = Cursors.Hand
            };
            btnKill.FlatAppearance.BorderSize = 0;
            btnKill.Click += (s, e) => KillGame();
            launchSection.Controls.Add(btnKill);

            lblLaunchSub = new Label
            {
                Text = "100% Offline Vault | Zero Cloud Needed | Direct Bytecode Injection",
                Font = new Font("Segoe UI", 8.5f),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point((892 - 380) / 2, 90),
                Anchor = AnchorStyles.Top,
                AutoSize = true
            };
            launchSection.Controls.Add(lblLaunchSub);

            pnlPlay.Controls.Add(launchSection);

            // Bottom Quick Info Row (Account Pill on Left, Quick Folders on Right)
            Panel bottomRow = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(0, 10, 0, 0)
            };

            // Player Profile Card
            Panel cardProfile = new Panel
            {
                Location = new Point(0, 10),
                Size = new Size(420, 140),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            cardProfile.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, cardProfile.Width - 1, cardProfile.Height - 1);
                }
            };
            Label lblCardProfTitle = new Label
            {
                Text = "PLAYER PROFILE (OFFLINE / MSA)",
                Font = new Font("Segoe UI", 8.8f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(14, 12),
                AutoSize = true
            };
            cardProfile.Controls.Add(lblCardProfTitle);

            cbAccounts = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9.5f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(16, 40),
                Size = new Size(250, 28)
            };
            List<string> accounts = LaunchEngine.LoadAccounts();
            foreach (string acc in accounts) cbAccounts.Items.Add(acc);
            if (cbAccounts.Items.Count > 0) cbAccounts.SelectedIndex = 0;
            cardProfile.Controls.Add(cbAccounts);

            txtNewUser = new TextBox
            {
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.FromArgb(203, 213, 225),
                Font = new Font("Segoe UI", 9f),
                BorderStyle = BorderStyle.FixedSingle,
                Location = new Point(16, 82),
                Size = new Size(250, 26)
            };
            txtNewUser.Text = "Add username...";
            txtNewUser.GotFocus += (s, e) => { if (txtNewUser.Text == "Add username...") txtNewUser.Text = ""; };
            cardProfile.Controls.Add(txtNewUser);

            btnAddUser = new Button
            {
                Text = "+ Add",
                BackColor = Color.FromArgb(39, 45, 75),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                Location = new Point(276, 80),
                Size = new Size(80, 28),
                Cursor = Cursors.Hand
            };
            btnAddUser.FlatAppearance.BorderSize = 0;
            btnAddUser.Click += (s, e) =>
            {
                string u = txtNewUser.Text.Trim();
                if (!string.IsNullOrEmpty(u) && u != "Add username...")
                {
                    if (!cbAccounts.Items.Contains(u)) cbAccounts.Items.Add(u);
                    cbAccounts.SelectedItem = u;
                    LaunchEngine.SaveOfflineAccount(u);
                    txtNewUser.Text = "";
                }
            };
            cardProfile.Controls.Add(btnAddUser);
            bottomRow.Controls.Add(cardProfile);

            // Quick Folders Card
            Panel cardFolders = new Panel
            {
                Location = new Point(440, 10),
                Size = new Size(450, 140),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            cardFolders.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, cardFolders.Width - 1, cardFolders.Height - 1);
                }
            };
            Label lblCardFoldTitle = new Label
            {
                Text = "QUICK SHORTCUTS & ASSETS",
                Font = new Font("Segoe UI", 8.8f, FontStyle.Bold),
                ForeColor = Color.FromArgb(56, 189, 248),
                Location = new Point(14, 12),
                AutoSize = true
            };
            cardFolders.Controls.Add(lblCardFoldTitle);

            Button btnPacks = CreateSmallButton("Resource Packs", 16, 42, () => OpenFolder("resourcepacks"));
            Button btnShaders = CreateSmallButton("Shader Packs", 126, 42, () => OpenFolder("shaderpacks"));
            Button btnScreens = CreateSmallButton("Screenshots", 226, 42, () => OpenFolder("screenshots"));
            Button btnGameDir = CreateSmallButton("Game Folder", 16, 82, () => OpenFolder(""));
            Button btnFastLaunch = CreateSmallButton("Fast Direct Launch", 140, 82, () => LaunchEngine.LaunchDirect(cbAccounts.SelectedItem as string, tbRam.Value * 256));

            cardFolders.Controls.Add(btnPacks);
            cardFolders.Controls.Add(btnShaders);
            cardFolders.Controls.Add(btnScreens);
            cardFolders.Controls.Add(btnGameDir);
            cardFolders.Controls.Add(btnFastLaunch);

            bottomRow.Controls.Add(cardFolders);
            pnlPlay.Controls.Add(bottomRow);
        }

        private void BuildSettingsTab(Panel host, int defaultRam)
        {
            pnlSettings = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                Visible = false
            };
            host.Controls.Add(pnlSettings);

            Label lblSetHeading = new Label
            {
                Text = "LAUNCHER CONFIGURATION & MEMORY",
                Font = new Font("Segoe UI", 12f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(0, 5),
                AutoSize = true
            };
            pnlSettings.Controls.Add(lblSetHeading);

            // RAM Card
            Panel cardRam = new Panel
            {
                Location = new Point(0, 40),
                Size = new Size(890, 115),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            cardRam.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, cardRam.Width - 1, cardRam.Height - 1);
                }
            };

            int maxRamSlider = Program.Is64BitOS ? 8192 : 1024;
            int minRamSlider = 512;
            int initRam = defaultRam > 0 ? defaultRam : (Program.Is64BitOS ? 2048 : 768);
            if (initRam > maxRamSlider) initRam = maxRamSlider;
            if (initRam < minRamSlider) initRam = minRamSlider;

            Label lblRamTitle = new Label
            {
                Text = "MEMORY ALLOCATION (RAM)",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(16, 14),
                AutoSize = true
            };
            cardRam.Controls.Add(lblRamTitle);

            lblRamVal = new Label
            {
                Text = string.Format("{0} MB ({1:0.0} GB)", initRam, initRam / 1024.0),
                Font = new Font("Segoe UI", 10f, FontStyle.Bold),
                ForeColor = Color.FromArgb(52, 211, 153),
                Location = new Point(cardRam.Width - 180, 12),
                AutoSize = true
            };
            cardRam.Controls.Add(lblRamVal);

            tbRam = new TrackBar
            {
                Minimum = minRamSlider / 256,
                Maximum = maxRamSlider / 256,
                Value = initRam / 256,
                TickFrequency = 2,
                Location = new Point(16, 45),
                Size = new Size(850, 45)
            };
            tbRam.Scroll += (s, e) =>
            {
                int mb = tbRam.Value * 256;
                lblRamVal.Text = string.Format("{0} MB ({1:0.0} GB)", mb, mb / 1024.0);
            };
            cardRam.Controls.Add(tbRam);
            pnlSettings.Controls.Add(cardRam);

            // Optimization Preset Card
            Panel cardPreset = new Panel
            {
                Location = new Point(0, 175),
                Size = new Size(890, 95),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            cardPreset.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, cardPreset.Width - 1, cardPreset.Height - 1);
                }
            };
            Label lblPreTitle = new Label
            {
                Text = "PERFORMANCE & OPTIMIZATION PRESET",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(16, 14),
                AutoSize = true
            };
            cardPreset.Controls.Add(lblPreTitle);

            cbPreset = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9.5f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(16, 42),
                Size = new Size(500, 28)
            };
            cbPreset.Items.Add("Ultra FPS Boost (Lunar Style - Recommended)");
            cbPreset.Items.Add("Competitive PvP (Low Latency / G1GC)");
            cbPreset.Items.Add("Balanced (Low Memory Garbage Collection)");
            cbPreset.Items.Add("Low End PC (Optimized Heap Footprint)");
            cbPreset.SelectedIndex = 0;
            cardPreset.Controls.Add(cbPreset);
            pnlSettings.Controls.Add(cardPreset);

            // Resolution Card
            Panel cardRes = new Panel
            {
                Location = new Point(0, 290),
                Size = new Size(890, 95),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            cardRes.Paint += (s, e) =>
            {
                using (Pen p = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawRectangle(p, 0, 0, cardRes.Width - 1, cardRes.Height - 1);
                }
            };
            Label lblResTitle = new Label
            {
                Text = "DISPLAY RESOLUTION",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(16, 14),
                AutoSize = true
            };
            cardRes.Controls.Add(lblResTitle);

            cbResolution = new ComboBox
            {
                DropDownStyle = ComboBoxStyle.DropDownList,
                BackColor = Color.FromArgb(24, 28, 48),
                ForeColor = Color.White,
                Font = new Font("Segoe UI", 9.5f),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(16, 42),
                Size = new Size(500, 28)
            };
            cbResolution.Items.Add("1280 x 720 (Standard HD)");
            cbResolution.Items.Add("1920 x 1080 (Full HD)");
            cbResolution.Items.Add("2560 x 1440 (2K QHD)");
            cbResolution.Items.Add("Fullscreen Mode");
            cbResolution.SelectedIndex = 0;
            cardRes.Controls.Add(cbResolution);
            pnlSettings.Controls.Add(cardRes);
        }

        private void BuildLogsTab(Panel host)
        {
            pnlLogs = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                Visible = false
            };
            host.Controls.Add(pnlLogs);

            Panel header = new Panel
            {
                Dock = DockStyle.Top,
                Height = 35
            };
            Label lblLogTitle = new Label
            {
                Text = "LIVE GAME CONSOLE & DIAGNOSTICS",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(0, 6),
                AutoSize = true
            };
            header.Controls.Add(lblLogTitle);

            Button btnClear = CreateSmallButton("Clear Logs", 760, 2, () => rtbConsole.Clear());
            header.Controls.Add(btnClear);
            pnlLogs.Controls.Add(header);

            rtbConsole = new RichTextBox
            {
                Dock = DockStyle.Fill,
                BackColor = Color.FromArgb(7, 8, 14),
                ForeColor = Color.FromArgb(226, 232, 240),
                Font = new Font("Consolas", 8.8f),
                BorderStyle = BorderStyle.None,
                ReadOnly = true
            };
            pnlLogs.Controls.Add(rtbConsole);

            AppendLog("[Launcher] Cosmic Client Offcloud Edition Ready.", Color.FromArgb(56, 189, 248));
            AppendLog("[Launcher] System: " + (Program.Is64BitOS ? "64-bit (x64)" : "32-bit (x86)") + " Windows", Color.FromArgb(148, 163, 184));
        }

        private Button CreateSmallButton(string text, int x, int y, Action onClick)
        {
            Button b = new Button
            {
                Text = text,
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(203, 213, 225),
                BackColor = Color.FromArgb(24, 28, 48),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(x, y),
                Size = new Size(95, 30),
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
                lblJavaStatus.Text = "Java Runtime: Missing from system";
                lblJavaStatus.ForeColor = Color.FromArgb(251, 191, 36);
                AppendLog("[JavaManager] No working Java found. Zulu 19 JRE auto-download is available.", Color.FromArgb(251, 191, 36));
            }
        }

        private void LaunchGame()
        {
            if (LaunchEngine.CurrentGameProcess != null && !LaunchEngine.CurrentGameProcess.HasExited)
            {
                MessageBox.Show("Cosmic Client is already running!", "Cosmic Client", MessageBoxButtons.OK, MessageBoxIcon.Information);
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
                AppendLog("[CLIENT] STARTING COSMIC CLIENT 1.8.9 (OFFLINE MODE)", Color.FromArgb(192, 132, 252));
                AppendLog("=======================================================", Color.FromArgb(124, 58, 237));
                AppendLog(summary, Color.FromArgb(203, 213, 225));

                btnLaunch.Text = "RUNNING COSMIC CLIENT...";
                btnLaunch.BackColor = Color.FromArgb(30, 41, 59);
                lblLaunchSub.Visible = false;
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
                        btnLaunch.Text = "LAUNCH COSMIC CLIENT 1.8.9";
                        btnLaunch.BackColor = Color.FromArgb(124, 58, 237);
                        lblLaunchSub.Visible = true;
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
                btnLaunch.Text = "LAUNCH COSMIC CLIENT 1.8.9";
                btnLaunch.BackColor = Color.FromArgb(124, 58, 237);
                lblLaunchSub.Visible = true;
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
'@

$csharpDirect = @'
using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Windows.Forms;

namespace CosmicClientDirect
{
    public static class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls12;
            string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            string tempPath = Path.GetTempPath().TrimEnd('\\', '/');

            string installDir = Path.Combine(baseDir, "CosmicClient-x64");
            if (!File.Exists(Path.Combine(installDir, "1.8", "CosmicClient-1.8.9.jar")))
            {
                if (File.Exists(Path.Combine(baseDir, "1.8", "CosmicClient-1.8.9.jar")))
                    installDir = baseDir;
            }

            string clientJar = Path.Combine(installDir, "1.8", "CosmicClient-1.8.9.jar");
            string directBat = Path.Combine(baseDir, "Launch-Cosmic-Direct.bat");

            if (baseDir.StartsWith(tempPath, StringComparison.OrdinalIgnoreCase) || !File.Exists(clientJar))
            {
                MessageBox.Show(
                    "Cosmic Client must be extracted before running!\n\n" +
                    "1. Close this window\n" +
                    "2. Right-click the ZIP archive and select 'Extract All...'\n" +
                    "3. Open the extracted folder and run CosmicClient-Direct.exe",
                    "Cosmic Client - Extraction Required",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Warning
                );
                return;
            }

            // Launch via Direct BAT or Launcher
            if (File.Exists(directBat))
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = "cmd.exe",
                    Arguments = "/c \"" + directBat + "\"",
                    WorkingDirectory = baseDir,
                    UseShellExecute = true
                };
                Process.Start(psi);
            }
            else
            {
                string launcherExe = Path.Combine(baseDir, "CosmicClientLauncher.exe");
                if (File.Exists(launcherExe))
                {
                    Process.Start(new ProcessStartInfo { FileName = launcherExe, Arguments = "--direct", WorkingDirectory = baseDir, UseShellExecute = true });
                }
            }
        }
    }
}
'@

$directOut = Join-Path $basePath "CosmicClient-Direct.exe"
$launcherOut = Join-Path $basePath "CosmicClientLauncher.exe"
$offcloudOut = Join-Path $basePath "CosmicClientOffcloud.exe"

$provider = New-Object Microsoft.CSharp.CSharpCodeProvider

# Compile CosmicClientLauncher.exe with cosmic.ico
$paramsLauncher = New-Object System.CodeDom.Compiler.CompilerParameters
$paramsLauncher.GenerateExecutable = $true
$paramsLauncher.OutputAssembly = $launcherOut
$paramsLauncher.CompilerOptions = "/win32icon:`"$icoPath`" /platform:anycpu /target:winexe /optimize+"
$paramsLauncher.ReferencedAssemblies.Add("System.dll") | Out-Null
$paramsLauncher.ReferencedAssemblies.Add("System.Windows.Forms.dll") | Out-Null
$paramsLauncher.ReferencedAssemblies.Add("System.Drawing.dll") | Out-Null
$paramsLauncher.ReferencedAssemblies.Add("System.IO.Compression.dll") | Out-Null
$paramsLauncher.ReferencedAssemblies.Add("System.IO.Compression.FileSystem.dll") | Out-Null

$resLauncher = $provider.CompileAssemblyFromSource($paramsLauncher, $csharpLauncher)
if ($resLauncher.Errors.Count -gt 0) {
    Write-Host "[Build Error in Launcher]" -ForegroundColor Red
    $resLauncher.Errors | ForEach-Object { Write-Host $_.ErrorText -ForegroundColor Red }
    exit 1
}

# Compile CosmicClient-Direct.exe with cosmic.ico
$paramsDirect = New-Object System.CodeDom.Compiler.CompilerParameters
$paramsDirect.GenerateExecutable = $true
$paramsDirect.OutputAssembly = $directOut
$paramsDirect.CompilerOptions = "/win32icon:`"$icoPath`" /platform:anycpu /target:winexe /optimize+"
$paramsDirect.ReferencedAssemblies.Add("System.dll") | Out-Null
$paramsDirect.ReferencedAssemblies.Add("System.Windows.Forms.dll") | Out-Null
$paramsDirect.ReferencedAssemblies.Add("System.Drawing.dll") | Out-Null

$resDirect = $provider.CompileAssemblyFromSource($paramsDirect, $csharpDirect)
if ($resDirect.Errors.Count -gt 0) {
    Write-Host "[Build Error in Direct]" -ForegroundColor Red
    $resDirect.Errors | ForEach-Object { Write-Host $_.ErrorText -ForegroundColor Red }
    exit 1
}

$distPortable = Join-Path $basePath "dist\CosmicClient.exe"
if (Test-Path $distPortable) {
    Copy-Item $distPortable $launcherOut -Force
    Copy-Item $distPortable $offcloudOut -Force
} else {
    Copy-Item $launcherOut $offcloudOut -Force
}

Write-Host "[Build] Successfully compiled Lunar Client-style AnyCPU CosmicClientLauncher.exe, CosmicClientOffcloud.exe, and CosmicClient-Direct.exe with official vector-perfect icon!" -ForegroundColor Green
