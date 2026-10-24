// Самораспаковщик YouLauncher-Setup.exe. К концу этого exe дописан zip с портативной сборкой и хвост:
// [zip][8 байт: длина zip, little-endian]["YLSETUP1"]. Стаб распаковывает zip во временную папку,
// показывает небольшую заставку и запускает YouLauncher.exe --setup: окно установщика рисует сама программа
// (Material 3, Compose), поэтому в установщике нет второй копии Java. Временная папка удаляется после выхода.
// Собирается компилятором C# из .NET Framework 4 (есть в каждой Windows 10/11), см. задачу Gradle packageSetup.
using System;
using System.Diagnostics;
using System.Drawing;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

static class SetupStub
{
    const string Magic = "YLSETUP1";

    [DllImport("user32.dll")] static extern bool SetProcessDPIAware();

    [STAThread]
    static int Main()
    {
        try { SetProcessDPIAware(); } catch { }
        Application.EnableVisualStyles();
        bool ru = CultureInfo.CurrentUICulture.TwoLetterISOLanguageName == "ru";
        string temp = Path.Combine(Path.GetTempPath(), "YouLauncherSetup-" + Process.GetCurrentProcess().Id);
        Exception failure = null;
        Process setup = null;

        using (var splash = new Splash(ru ? "Подготовка установки…" : "Preparing setup…"))
        {
            var worker = new Thread(() =>
            {
                try
                {
                    Extract(Application.ExecutablePath, temp);
                    string appDir = Path.Combine(temp, "YouLauncher-App");
                    var info = new ProcessStartInfo(Path.Combine(appDir, "YouLauncher.exe"), "--setup")
                    {
                        WorkingDirectory = appDir,
                        UseShellExecute = false,
                    };
                    setup = Process.Start(info);
                    // Заставка висит, пока не появится окно установщика (Java стартует пару секунд).
                    WaitForWindow(appDir, TimeSpan.FromSeconds(25));
                }
                catch (Exception e) { failure = e; }
                splash.BeginInvoke(new Action(splash.Close));
            });
            worker.IsBackground = true;
            splash.Shown += (s, e) => worker.Start();
            Application.Run(splash);
        }

        if (failure != null)
        {
            MessageBox.Show((ru ? "Не удалось подготовить установку: " : "Could not prepare setup: ") + failure.Message, "YouLauncher", MessageBoxButtons.OK, MessageBoxIcon.Error);
            Cleanup(temp);
            return 1;
        }
        if (setup != null) setup.WaitForExit();
        Cleanup(temp);
        return 0;
    }

    /** Читает хвост файла, копирует zip во временный файл и распаковывает. */
    static void Extract(string exe, string target)
    {
        if (Directory.Exists(target)) Directory.Delete(target, true);
        Directory.CreateDirectory(target);
        string zipPath = Path.Combine(target, "payload.zip");
        using (var fs = new FileStream(exe, FileMode.Open, FileAccess.Read, FileShare.Read))
        {
            var tail = new byte[16];
            fs.Seek(-16, SeekOrigin.End);
            fs.Read(tail, 0, 16);
            if (Encoding.ASCII.GetString(tail, 8, 8) != Magic) throw new InvalidDataException("payload not found");
            long length = BitConverter.ToInt64(tail, 0);
            fs.Seek(-16 - length, SeekOrigin.End);
            using (var zip = new FileStream(zipPath, FileMode.Create, FileAccess.Write))
            {
                var buffer = new byte[1 << 20];
                long left = length;
                while (left > 0)
                {
                    int n = fs.Read(buffer, 0, (int)Math.Min(buffer.Length, left));
                    if (n <= 0) throw new EndOfStreamException();
                    zip.Write(buffer, 0, n);
                    left -= n;
                }
            }
        }
        ZipFile.ExtractToDirectory(zipPath, target);
        File.Delete(zipPath);
    }

    static void WaitForWindow(string appDir, TimeSpan timeout)
    {
        var until = DateTime.Now + timeout;
        while (DateTime.Now < until)
        {
            foreach (var p in Process.GetProcessesByName("YouLauncher"))
            {
                try
                {
                    if (p.MainWindowHandle != IntPtr.Zero && p.MainModule.FileName.StartsWith(appDir, StringComparison.OrdinalIgnoreCase)) return;
                }
                catch { }
            }
            Thread.Sleep(200);
        }
    }

    static void Cleanup(string dir)
    {
        for (int i = 0; i < 10; i++)
        {
            try { if (Directory.Exists(dir)) Directory.Delete(dir, true); return; }
            catch { Thread.Sleep(500); }
        }
    }

    /** Небольшая заставка на время распаковки: название и бегущий индикатор. */
    sealed class Splash : Form
    {
        public Splash(string text)
        {
            FormBorderStyle = FormBorderStyle.None;
            StartPosition = FormStartPosition.CenterScreen;
            ShowInTaskbar = true;
            Text = "YouLauncher";
            Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
            BackColor = Color.FromArgb(18, 28, 22);
            AutoScaleMode = AutoScaleMode.Dpi;
            ClientSize = new Size(420, 150);
            var title = new Label
            {
                Text = "YouLauncher",
                ForeColor = Color.FromArgb(214, 240, 222),
                Font = new Font("Segoe UI Semibold", 20f),
                AutoSize = true,
                Location = new Point(28, 24),
            };
            var caption = new Label
            {
                Text = text,
                ForeColor = Color.FromArgb(170, 196, 180),
                Font = new Font("Segoe UI", 10.5f),
                AutoSize = true,
                Location = new Point(30, 70),
            };
            var bar = new ProgressBar { Style = ProgressBarStyle.Marquee, MarqueeAnimationSpeed = 25, Location = new Point(30, 108), Size = new Size(360, 6) };
            Controls.Add(title);
            Controls.Add(caption);
            Controls.Add(bar);
        }
    }
}
