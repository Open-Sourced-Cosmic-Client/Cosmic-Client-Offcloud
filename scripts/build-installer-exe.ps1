param(
    [switch]$SkipPayloadCompress
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

$basePath = (Get-Item $PSScriptRoot).Parent.FullName
$icoPath = Join-Path $basePath "cosmic.ico"
$payloadZip = Join-Path $basePath "installer-payload.zip"

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "=== BUILDING COSMIC CLIENT LUNAR-STYLE INSTALLER ===" -ForegroundColor Cyan
Write-Host "=== (100% Offline Vault, x86/x64 Support, No Cloud)  ==" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

# 1. Ensure Ultra-HD Vector Icon Exists
if (-not (Test-Path $icoPath)) {
    Write-Host "`n[1/4] Generating official Ultra-HD vector cosmic.ico..."
    & (Join-Path $PSScriptRoot "make-hd-cosmic-ico.ps1")
} else {
    Write-Host "`n[1/4] Verified Ultra-HD cosmic.ico exists." -ForegroundColor Green
}

# 2. Ensure Standalone Launchers are Built with Latest Lunar Style
Write-Host "`n[2/4] Compiling latest Lunar Client-style launchers..."
& (Join-Path $PSScriptRoot "build-windows-launchers.ps1")

$launcherExe = Join-Path $basePath "CosmicClientLauncher.exe"
$directExe = Join-Path $basePath "CosmicClient-Direct.exe"

# 3. Assemble Offline Payload Archive
Write-Host "`n[3/4] Packaging complete embedded offline vault (client, assets, agent, x32 & x64 JREs)..."
if (-not $SkipPayloadCompress -or -not (Test-Path $payloadZip)) {
    Write-Host " -> Running high-compression payload packaging pipeline..."
    & python (Join-Path $PSScriptRoot "package-payload.py")
}

if (-not (Test-Path $payloadZip)) {
    Write-Host "[ERROR] Failed to create installer payload zip." -ForegroundColor Red
    exit 1
}

$zipSizeMb = (Get-Item $payloadZip).Length / 1MB
Write-Host (" -> Offline vault package size: {0:0.0} MB" -f $zipSizeMb) -ForegroundColor Green

# 4. Standalone C# Installer Source Code with Lunar Client Aesthetics
$csharpInstaller = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

namespace CosmicClientInstaller
{
    public static class Program
    {
        [DllImport("user32.dll")]
        public static extern bool ReleaseCapture();
        [DllImport("user32.dll")]
        public static extern int SendMessage(IntPtr hWnd, int Msg, int wParam, int lParam);

        public const int WM_NCLBUTTONDOWN = 0xA1;
        public const int HTCAPTION = 0x2;

        public static bool Is64BitOS;
        public static string DefaultInstallPath;
        public static string ResourceZipName;

        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            Is64BitOS = Environment.Is64BitOperatingSystem;

            // Default target: %LOCALAPPDATA%\CosmicClient
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            DefaultInstallPath = Path.Combine(localAppData, "CosmicClient");

            // Check command line flags
            bool silent = false;
            bool directLaunch = false;
            string customTarget = null;

            for (int i = 0; i < args.Length; i++)
            {
                string a = args[i].ToLowerInvariant();
                if (a == "--silent" || a == "-s" || a == "/s" || a == "/silent") silent = true;
                if (a == "--direct" || a == "-d" || a == "/direct") directLaunch = true;
                if ((a == "--dir" || a == "-o" || a == "--path") && i + 1 < args.Length) customTarget = args[++i];
            }

            if (!string.IsNullOrEmpty(customTarget))
            {
                DefaultInstallPath = Path.GetFullPath(customTarget);
            }

            // Locate embedded payload
            Assembly asm = Assembly.GetExecutingAssembly();
            foreach (string n in asm.GetManifestResourceNames())
            {
                if (n.EndsWith("installer-payload.zip", StringComparison.OrdinalIgnoreCase) ||
                    n.EndsWith("payload.zip", StringComparison.OrdinalIgnoreCase))
                {
                    ResourceZipName = n;
                    break;
                }
            }

            if (string.IsNullOrEmpty(ResourceZipName))
            {
                MessageBox.Show(
                    "CRITICAL ERROR: Embedded offline payload is missing from this installer executable.",
                    "Cosmic Client Installer",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                return;
            }

            if (silent)
            {
                RunSilentInstall(DefaultInstallPath, directLaunch);
                return;
            }

            Application.Run(new InstallerForm(DefaultInstallPath, directLaunch));
        }

        public static void DrawVectorLogo(Graphics g, float cx, float cy, float size)
        {
            float scale = size / 256.0f;
            var state = g.Save();
            g.SmoothingMode = SmoothingMode.HighQuality;
            g.InterpolationMode = InterpolationMode.HighQualityBicubic;

            // 1. Outer Glow
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

            // 2. Back of Orbital Ring
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

            // 3. Planet Body
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

            // 4. Front of Orbital Ring
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

            // 5. Glowing Central Core
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

            // 6. Star sparkles
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

        public static void RunSilentInstall(string targetDir, bool directLaunch)
        {
            try
            {
                Directory.CreateDirectory(targetDir);
                Assembly asm = Assembly.GetExecutingAssembly();
                using (Stream stream = asm.GetManifestResourceStream(ResourceZipName))
                {
                    if (stream == null) return;
                    using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                    {
                        foreach (ZipArchiveEntry entry in archive.Entries)
                        {
                            try
                            {
                                string destPath = Path.GetFullPath(Path.Combine(targetDir, entry.FullName));
                                if (string.IsNullOrEmpty(entry.Name) || entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
                                {
                                    Directory.CreateDirectory(destPath);
                                    continue;
                                }
                                string parent = Path.GetDirectoryName(destPath);
                                if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                                entry.ExtractToFile(destPath, true);
                            }
                            catch { }
                        }
                    }
                }

                string launcherExe = Path.Combine(targetDir, "CosmicClientLauncher.exe");
                string icoPath = Path.Combine(targetDir, "cosmic.ico");

                ShortcutHelper.CreateDesktopShortcut(launcherExe, icoPath, "Cosmic Client");
                ShortcutHelper.CreateStartMenuShortcut(launcherExe, icoPath, "Cosmic Client");

                if (directLaunch)
                {
                    string directExe = Path.Combine(targetDir, "CosmicClient-Direct.exe");
                    if (File.Exists(directExe))
                        Process.Start(new ProcessStartInfo { FileName = directExe, WorkingDirectory = targetDir });
                    else if (File.Exists(launcherExe))
                        Process.Start(new ProcessStartInfo { FileName = launcherExe, Arguments = "--direct", WorkingDirectory = targetDir });
                }
            }
            catch { }
        }
    }

    public static class ShortcutHelper
    {
        public static void CreateDesktopShortcut(string targetExe, string iconPath, string name)
        {
            try
            {
                string desktop = Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory);
                string lnk = Path.Combine(desktop, name + ".lnk");
                CreateShortcut(lnk, targetExe, Path.GetDirectoryName(targetExe), iconPath, "Cosmic Client 1.8.9 Offcloud Edition");
            }
            catch { }
        }

        public static void CreateStartMenuShortcut(string targetExe, string iconPath, string name)
        {
            try
            {
                string progDir = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
                string groupDir = Path.Combine(progDir, "Cosmic Client");
                Directory.CreateDirectory(groupDir);
                string lnk = Path.Combine(groupDir, name + ".lnk");
                CreateShortcut(lnk, targetExe, Path.GetDirectoryName(targetExe), iconPath, "Cosmic Client 1.8.9 Offcloud Edition");
            }
            catch { }
        }

        private static void CreateShortcut(string shortcutPath, string targetPath, string workingDir, string iconPath, string description)
        {
            try
            {
                Type shellType = Type.GetTypeFromProgID("WScript.Shell");
                if (shellType == null) return;
                object shell = Activator.CreateInstance(shellType);
                object shortcut = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { shortcutPath });
                Type sType = shortcut.GetType();
                sType.InvokeMember("TargetPath", BindingFlags.SetProperty, null, shortcut, new object[] { targetPath });
                sType.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, shortcut, new object[] { workingDir });
                if (!string.IsNullOrEmpty(iconPath) && File.Exists(iconPath))
                {
                    sType.InvokeMember("IconLocation", BindingFlags.SetProperty, null, shortcut, new object[] { iconPath + ",0" });
                }
                sType.InvokeMember("Description", BindingFlags.SetProperty, null, shortcut, new object[] { description });
                sType.InvokeMember("Save", BindingFlags.InvokeMethod, null, shortcut, null);
            }
            catch { }
        }
    }

    public class InstallerForm : Form
    {
        private Panel pnlTitleBar;
        private Panel pnlContent;
        private Panel pnlSetup;
        private Panel pnlProgress;
        private Panel pnlSuccess;

        private TextBox txtPath;
        private CheckBox chkDesktop;
        private CheckBox chkStartMenu;
        private CheckBox chkLaunchNow;
        private Button btnInstall;

        private Label lblStepTitle;
        private Label lblFileDetail;
        private ProgressBar pbInstall;
        private Label lblPercent;

        private string installDir;
        private bool directLaunchFlag;

        public InstallerForm(string defaultPath, bool directLaunch)
        {
            this.installDir = defaultPath;
            this.directLaunchFlag = directLaunch;
            InitializeUI();
        }

        private void InitializeUI()
        {
            this.Text = "Cosmic Client - Setup";
            this.Size = new Size(720, 500);
            this.FormBorderStyle = FormBorderStyle.None;
            this.StartPosition = FormStartPosition.CenterScreen;
            this.BackColor = Color.FromArgb(11, 13, 23); // #0B0D17 Deep Space Obsidian
            this.ForeColor = Color.FromArgb(241, 245, 249);

            try
            {
                this.Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            }
            catch { }

            // Title Bar
            pnlTitleBar = new Panel
            {
                Dock = DockStyle.Top,
                Height = 48,
                BackColor = Color.FromArgb(16, 19, 34)
            };
            pnlTitleBar.MouseDown += (s, e) =>
            {
                if (e.Button == MouseButtons.Left)
                {
                    Program.ReleaseCapture();
                    Program.SendMessage(this.Handle, Program.WM_NCLBUTTONDOWN, Program.HTCAPTION, 0);
                }
            };
            pnlTitleBar.Paint += (s, e) =>
            {
                Program.DrawVectorLogo(e.Graphics, 24, 24, 28);
                using (Pen borderPen = new Pen(Color.FromArgb(30, 41, 59), 1f))
                {
                    e.Graphics.DrawLine(borderPen, 0, 47, pnlTitleBar.Width, 47);
                }
            };

            Label lblApp = new Label
            {
                Text = "COSMIC CLIENT",
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(48, 14),
                AutoSize = true
            };
            pnlTitleBar.Controls.Add(lblApp);

            Label lblAppTag = new Label
            {
                Text = "OFFCLOUD SETUP",
                Font = new Font("Segoe UI", 8f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(162, 16),
                AutoSize = true
            };
            pnlTitleBar.Controls.Add(lblAppTag);

            Button btnClose = new Button
            {
                Text = "X",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(44, 48),
                Location = new Point(this.Width - 44, 0),
                Cursor = Cursors.Hand
            };
            btnClose.FlatAppearance.BorderSize = 0;
            btnClose.FlatAppearance.MouseOverBackColor = Color.FromArgb(225, 29, 72);
            btnClose.Click += (s, e) => Application.Exit();
            pnlTitleBar.Controls.Add(btnClose);

            Button btnMin = new Button
            {
                Text = "-",
                Font = new Font("Segoe UI", 11f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                BackColor = Color.Transparent,
                FlatStyle = FlatStyle.Flat,
                Size = new Size(44, 48),
                Location = new Point(this.Width - 88, 0),
                Cursor = Cursors.Hand
            };
            btnMin.FlatAppearance.BorderSize = 0;
            btnMin.FlatAppearance.MouseOverBackColor = Color.FromArgb(30, 41, 59);
            btnMin.Click += (s, e) => this.WindowState = FormWindowState.Minimized;
            pnlTitleBar.Controls.Add(btnMin);

            this.Controls.Add(pnlTitleBar);

            pnlContent = new Panel
            {
                Dock = DockStyle.Fill,
                Padding = new Padding(24, 18, 24, 20)
            };
            this.Controls.Add(pnlContent);

            BuildSetupPanel();
            BuildProgressPanel();
            BuildSuccessPanel();

            bool alreadyInstalled = File.Exists(Path.Combine(installDir, "CosmicClientLauncher.exe")) &&
                                   File.Exists(Path.Combine(installDir, "1.8", "CosmicClient-1.8.9.jar"));
            if (alreadyInstalled)
            {
                btnInstall.Text = "REINSTALL / UPDATE COSMIC CLIENT";
            }

            pnlSetup.BringToFront();
        }

        private void BuildSetupPanel()
        {
            pnlSetup = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent
            };
            pnlContent.Controls.Add(pnlSetup);

            // Banner Hero Box
            Panel pnlHero = new Panel
            {
                Location = new Point(0, 0),
                Size = new Size(672, 115),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            pnlHero.Paint += (s, e) =>
            {
                using (Pen pen = new Pen(Color.FromArgb(124, 58, 237), 1.5f))
                {
                    e.Graphics.DrawRectangle(pen, 0, 0, pnlHero.Width - 1, pnlHero.Height - 1);
                }
                Program.DrawVectorLogo(e.Graphics, 55, 57, 76);
            };

            Label lblHeroTitle = new Label
            {
                Text = "COSMIC CLIENT 1.8.9",
                Font = new Font("Segoe UI", 15f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(108, 16),
                AutoSize = true
            };
            pnlHero.Controls.Add(lblHeroTitle);

            Label lblHeroSub = new Label
            {
                Text = "Offcloud Minecraft Edition | Bytecode Agent Injected | Ultra FPS",
                Font = new Font("Segoe UI", 9.2f),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(110, 48),
                AutoSize = true
            };
            pnlHero.Controls.Add(lblHeroSub);

            string archText = Program.Is64BitOS ? "64-bit (x64) Ready" : "32-bit (x86) Ready";
            Label lblBadge1 = CreateBadge(archText, Color.FromArgb(52, 211, 153), 110, 78);
            Label lblBadge2 = CreateBadge("100% Offline Vault", Color.FromArgb(192, 132, 252), 260, 78);
            Label lblBadge3 = CreateBadge("No Cloud Needed", Color.FromArgb(56, 189, 248), 415, 78);
            pnlHero.Controls.Add(lblBadge1);
            pnlHero.Controls.Add(lblBadge2);
            pnlHero.Controls.Add(lblBadge3);

            pnlSetup.Controls.Add(pnlHero);

            // Configuration Section
            Label lblDirHeading = new Label
            {
                Text = "INSTALLATION DIRECTORY",
                Font = new Font("Segoe UI", 8.5f, FontStyle.Bold),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(4, 135),
                AutoSize = true
            };
            pnlSetup.Controls.Add(lblDirHeading);

            txtPath = new TextBox
            {
                Text = installDir,
                Font = new Font("Segoe UI", 9.5f),
                BackColor = Color.FromArgb(22, 27, 46),
                ForeColor = Color.White,
                BorderStyle = BorderStyle.FixedSingle,
                Location = new Point(4, 158),
                Size = new Size(550, 30)
            };
            pnlSetup.Controls.Add(txtPath);

            Button btnBrowse = new Button
            {
                Text = "Browse...",
                Font = new Font("Segoe UI", 9f, FontStyle.Bold),
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.FromArgb(226, 232, 240),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(564, 156),
                Size = new Size(104, 30),
                Cursor = Cursors.Hand
            };
            btnBrowse.FlatAppearance.BorderSize = 0;
            btnBrowse.Click += (s, e) =>
            {
                using (FolderBrowserDialog fbd = new FolderBrowserDialog())
                {
                    fbd.Description = "Select Cosmic Client Installation Folder";
                    fbd.SelectedPath = txtPath.Text;
                    if (fbd.ShowDialog() == DialogResult.OK)
                    {
                        txtPath.Text = fbd.SelectedPath;
                    }
                }
            };
            pnlSetup.Controls.Add(btnBrowse);

            chkDesktop = new CheckBox
            {
                Text = "Create Desktop Shortcut (Cosmic Client)",
                Font = new Font("Segoe UI", 9.2f),
                ForeColor = Color.FromArgb(226, 232, 240),
                Checked = true,
                Location = new Point(6, 205),
                AutoSize = true,
                Cursor = Cursors.Hand
            };
            pnlSetup.Controls.Add(chkDesktop);

            chkStartMenu = new CheckBox
            {
                Text = "Create Start Menu Shortcut",
                Font = new Font("Segoe UI", 9.2f),
                ForeColor = Color.FromArgb(226, 232, 240),
                Checked = true,
                Location = new Point(6, 235),
                AutoSize = true,
                Cursor = Cursors.Hand
            };
            pnlSetup.Controls.Add(chkStartMenu);

            chkLaunchNow = new CheckBox
            {
                Text = "Launch Cosmic Client when installation finishes",
                Font = new Font("Segoe UI", 9.2f),
                ForeColor = Color.FromArgb(192, 132, 252),
                Checked = true,
                Location = new Point(6, 265),
                AutoSize = true,
                Cursor = Cursors.Hand
            };
            pnlSetup.Controls.Add(chkLaunchNow);

            btnInstall = new Button
            {
                Text = "INSTALL COSMIC CLIENT (OFFLINE)",
                Font = new Font("Segoe UI", 11.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(124, 58, 237),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Location = new Point(4, 340),
                Size = new Size(668, 52),
                Cursor = Cursors.Hand
            };
            btnInstall.FlatAppearance.BorderSize = 0;
            btnInstall.FlatAppearance.MouseOverBackColor = Color.FromArgb(147, 51, 234);
            btnInstall.Click += (s, e) => StartInstallation();
            pnlSetup.Controls.Add(btnInstall);
        }

        private void BuildProgressPanel()
        {
            pnlProgress = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                Visible = false
            };
            pnlContent.Controls.Add(pnlProgress);

            Label lblInstalling = new Label
            {
                Text = "INSTALLING COSMIC CLIENT",
                Font = new Font("Segoe UI", 14f, FontStyle.Bold),
                ForeColor = Color.FromArgb(192, 132, 252),
                Location = new Point(4, 30),
                AutoSize = true
            };
            pnlProgress.Controls.Add(lblInstalling);

            lblStepTitle = new Label
            {
                Text = "Preparing offline vault...",
                Font = new Font("Segoe UI", 10.5f, FontStyle.Bold),
                ForeColor = Color.White,
                Location = new Point(6, 75),
                AutoSize = true
            };
            pnlProgress.Controls.Add(lblStepTitle);

            lblFileDetail = new Label
            {
                Text = "Extracting embedded files to disk...",
                Font = new Font("Consolas", 8.8f),
                ForeColor = Color.FromArgb(148, 163, 184),
                Location = new Point(6, 108),
                Size = new Size(660, 20),
                AutoEllipsis = true
            };
            pnlProgress.Controls.Add(lblFileDetail);

            pbInstall = new ProgressBar
            {
                Location = new Point(6, 140),
                Size = new Size(662, 30),
                Style = ProgressBarStyle.Continuous
            };
            pnlProgress.Controls.Add(pbInstall);

            lblPercent = new Label
            {
                Text = "0%",
                Font = new Font("Segoe UI", 12f, FontStyle.Bold),
                ForeColor = Color.FromArgb(52, 211, 153),
                Location = new Point(6, 180),
                AutoSize = true
            };
            pnlProgress.Controls.Add(lblPercent);
        }

        private void BuildSuccessPanel()
        {
            pnlSuccess = new Panel
            {
                Dock = DockStyle.Fill,
                BackColor = Color.Transparent,
                Visible = false
            };
            pnlContent.Controls.Add(pnlSuccess);

            Label lblDoneTitle = new Label
            {
                Text = "INSTALLATION COMPLETED SUCCESSFULLY",
                Font = new Font("Segoe UI", 14f, FontStyle.Bold),
                ForeColor = Color.FromArgb(52, 211, 153),
                Location = new Point(4, 25),
                AutoSize = true
            };
            pnlSuccess.Controls.Add(lblDoneTitle);

            Panel pnlSummary = new Panel
            {
                Location = new Point(6, 75),
                Size = new Size(662, 150),
                BackColor = Color.FromArgb(18, 22, 38)
            };
            pnlSummary.Paint += (s, e) =>
            {
                using (Pen pen = new Pen(Color.FromArgb(52, 211, 153), 1f))
                {
                    e.Graphics.DrawRectangle(pen, 0, 0, pnlSummary.Width - 1, pnlSummary.Height - 1);
                }
            };

            Label lblSumContent = new Label
            {
                Text = "Status: Installed & Ready to Play\n" +
                       "Architecture: " + (Program.Is64BitOS ? "64-bit (x64) Windows Native" : "32-bit (x86) Windows Native") + "\n" +
                       "Java Runtime: High-Performance OpenJDK Zulu 19 (Bundled)\n" +
                       "Agent Injection: Ready (Offline authentication & FPS optimization)\n\n" +
                       "You can launch Cosmic Client at any time using your Desktop shortcut\n" +
                       "or the Start Menu program group.",
                Font = new Font("Segoe UI", 9.2f),
                ForeColor = Color.FromArgb(203, 213, 225),
                Location = new Point(16, 16),
                AutoSize = true
            };
            pnlSummary.Controls.Add(lblSumContent);
            pnlSuccess.Controls.Add(pnlSummary);

            Button btnLaunchNow = new Button
            {
                Text = "LAUNCH COSMIC CLIENT NOW",
                Font = new Font("Segoe UI", 11.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(124, 58, 237),
                ForeColor = Color.White,
                FlatStyle = FlatStyle.Flat,
                Location = new Point(6, 250),
                Size = new Size(662, 52),
                Cursor = Cursors.Hand
            };
            btnLaunchNow.FlatAppearance.BorderSize = 0;
            btnLaunchNow.FlatAppearance.MouseOverBackColor = Color.FromArgb(147, 51, 234);
            btnLaunchNow.Click += (s, e) => LaunchInstalledGame();
            pnlSuccess.Controls.Add(btnLaunchNow);

            Button btnOpenDir = new Button
            {
                Text = "Open Install Folder",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.FromArgb(226, 232, 240),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(6, 318),
                Size = new Size(325, 42),
                Cursor = Cursors.Hand
            };
            btnOpenDir.FlatAppearance.BorderSize = 0;
            btnOpenDir.Click += (s, e) =>
            {
                try { Process.Start("explorer.exe", installDir); } catch { }
            };
            pnlSuccess.Controls.Add(btnOpenDir);

            Button btnExit = new Button
            {
                Text = "Close Setup",
                Font = new Font("Segoe UI", 9.5f, FontStyle.Bold),
                BackColor = Color.FromArgb(30, 41, 59),
                ForeColor = Color.FromArgb(226, 232, 240),
                FlatStyle = FlatStyle.Flat,
                Location = new Point(343, 318),
                Size = new Size(325, 42),
                Cursor = Cursors.Hand
            };
            btnExit.FlatAppearance.BorderSize = 0;
            btnExit.Click += (s, e) => Application.Exit();
            pnlSuccess.Controls.Add(btnExit);
        }

        private Label CreateBadge(string text, Color color, int x, int y)
        {
            Label lbl = new Label
            {
                Text = text,
                Font = new Font("Segoe UI", 8.3f, FontStyle.Bold),
                ForeColor = color,
                Location = new Point(x, y),
                AutoSize = true
            };
            return lbl;
        }

        private void StartInstallation()
        {
            installDir = txtPath.Text.Trim();
            if (string.IsNullOrEmpty(installDir))
            {
                MessageBox.Show("Please choose a valid destination directory.", "Cosmic Client Setup", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            pnlSetup.Visible = false;
            pnlProgress.Visible = true;
            pnlProgress.BringToFront();

            bool makeDesktop = chkDesktop.Checked;
            bool makeStartMenu = chkStartMenu.Checked;
            bool launchOnDone = chkLaunchNow.Checked || directLaunchFlag;

            Thread t = new Thread(() =>
            {
                try
                {
                    Directory.CreateDirectory(installDir);

                    Assembly asm = Assembly.GetExecutingAssembly();
                    using (Stream stream = asm.GetManifestResourceStream(Program.ResourceZipName))
                    {
                        if (stream == null) throw new Exception("Could not open embedded resource stream: " + Program.ResourceZipName);

                        using (ZipArchive archive = new ZipArchive(stream, ZipArchiveMode.Read))
                        {
                            int totalEntries = archive.Entries.Count;
                            int current = 0;

                            foreach (ZipArchiveEntry entry in archive.Entries)
                            {
                                current++;
                                try
                                {
                                    string destPath = Path.GetFullPath(Path.Combine(installDir, entry.FullName));
                                    if (string.IsNullOrEmpty(entry.Name) || entry.FullName.EndsWith("/") || entry.FullName.EndsWith("\\"))
                                    {
                                        Directory.CreateDirectory(destPath);
                                        continue;
                                    }
                                    string parent = Path.GetDirectoryName(destPath);
                                    if (!string.IsNullOrEmpty(parent)) Directory.CreateDirectory(parent);
                                    entry.ExtractToFile(destPath, true);
                                }
                                catch { }

                                if (current % 15 == 0 || current == totalEntries)
                                {
                                    int pct = (int)((current / (double)totalEntries) * 100);
                                    string fileName = entry.FullName;
                                    this.BeginInvoke((Action)(() =>
                                    {
                                        pbInstall.Value = Math.Min(100, Math.Max(0, pct));
                                        lblPercent.Text = pct + "%";

                                        if (pct < 15) lblStepTitle.Text = "[1/5] Extracting Core Game Engine & Agent...";
                                        else if (pct < 35) lblStepTitle.Text = "[2/5] Deploying Native Libraries (x86 & x64)...";
                                        else if (pct < 70) lblStepTitle.Text = "[3/5] Installing 1.8.9 Game Assets & Audio...";
                                        else if (pct < 95) lblStepTitle.Text = "[4/5] Configuring Bundled Java 19 Runtimes...";
                                        else lblStepTitle.Text = "[5/5] Finalizing Installation...";

                                        lblFileDetail.Text = string.Format("({0}/{1}) {2}", current, totalEntries, fileName);
                                    }));
                                }
                            }
                        }
                    }

                    string launcherExe = Path.Combine(installDir, "CosmicClientLauncher.exe");
                    string icoPath = Path.Combine(installDir, "cosmic.ico");

                    if (makeDesktop) ShortcutHelper.CreateDesktopShortcut(launcherExe, icoPath, "Cosmic Client");
                    if (makeStartMenu) ShortcutHelper.CreateStartMenuShortcut(launcherExe, icoPath, "Cosmic Client");

                    Thread.Sleep(300);

                    this.BeginInvoke((Action)(() =>
                    {
                        pnlProgress.Visible = false;
                        pnlSuccess.Visible = true;
                        pnlSuccess.BringToFront();

                        if (launchOnDone)
                        {
                            LaunchInstalledGame();
                        }
                    }));
                }
                catch (Exception ex)
                {
                    this.BeginInvoke((Action)(() =>
                    {
                        MessageBox.Show("Installation failed: " + ex.Message, "Cosmic Client Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                        pnlProgress.Visible = false;
                        pnlSetup.Visible = true;
                    }));
                }
            });
            t.IsBackground = true;
            t.Start();
        }

        private void LaunchInstalledGame()
        {
            try
            {
                string launcherExe = Path.Combine(installDir, "CosmicClientLauncher.exe");
                string directExe = Path.Combine(installDir, "CosmicClient-Direct.exe");

                if (directLaunchFlag && File.Exists(directExe))
                {
                    Process.Start(new ProcessStartInfo { FileName = directExe, WorkingDirectory = installDir });
                    Application.Exit();
                }
                else if (File.Exists(launcherExe))
                {
                    Process.Start(new ProcessStartInfo { FileName = launcherExe, WorkingDirectory = installDir });
                    Application.Exit();
                }
                else
                {
                    MessageBox.Show("Launcher executable not found at: " + launcherExe, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Could not start Cosmic Client: " + ex.Message, "Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
'@

# 5. Compile Universal Standalone Single-File Installer
Write-Host "`n[4/4] Compiling CosmicClient-Setup.exe with embedded offline payload..."
$outSetup = Join-Path $basePath "CosmicClient-Setup.exe"
$outInstaller = Join-Path $basePath "CosmicClient-Installer.exe"

$provider = New-Object Microsoft.CSharp.CSharpCodeProvider
$params = New-Object System.CodeDom.Compiler.CompilerParameters
$params.GenerateExecutable = $true
$params.OutputAssembly = $outSetup
$params.CompilerOptions = "/win32icon:`"$icoPath`" /resource:`"$payloadZip`",installer-payload.zip /platform:anycpu /target:winexe /optimize+"

$params.ReferencedAssemblies.Add("System.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.Windows.Forms.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.Drawing.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.IO.Compression.dll") | Out-Null
$params.ReferencedAssemblies.Add("System.IO.Compression.FileSystem.dll") | Out-Null

$res = $provider.CompileAssemblyFromSource($params, $csharpInstaller)
if ($res.Errors.Count -gt 0) {
    Write-Host "[Compilation Error in Installer]" -ForegroundColor Red
    $res.Errors | ForEach-Object { Write-Host $_.ErrorText -ForegroundColor Red }
    exit 1
}

Copy-Item $outSetup $outInstaller -Force

# Clean up temp payload zip to save disk space
if (Test-Path $payloadZip) { Remove-Item $payloadZip -Force }

$exeSizeMb = (Get-Item $outSetup).Length / 1MB
Write-Host ("`n==================================================================") -ForegroundColor Green
Write-Host ("=== [BUILD SUCCESS] Created Ultra-Smooth Universal Installer: ===") -ForegroundColor Green
Write-Host ("   -> CosmicClient-Setup.exe     ({0:0.0} MB)" -f $exeSizeMb) -ForegroundColor Cyan
Write-Host ("   -> CosmicClient-Installer.exe ({0:0.0} MB)" -f $exeSizeMb) -ForegroundColor Cyan
Write-Host ("==================================================================") -ForegroundColor Green
