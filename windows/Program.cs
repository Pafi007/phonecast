using System.Diagnostics;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.Drawing.Text;
using System.Globalization;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using QRCoder;

static class P
{
    [STAThread]
    static void Main() { ApplicationConfiguration.Initialize(); Application.Run(new Viewer()); }
}

class Viewer : Form
{
    const int Port = 5555;
    [DllImport("dwmapi.dll")] static extern int DwmSetWindowAttribute(IntPtr h, int a, ref int v, int s);

    string token = Tok(), ip = LocalIp();
    TcpClient? cli; NetworkStream? ns;
    Bitmap? frame, qr, blobs;
    float live, target, t;
    double last;
    readonly Stopwatch sw = Stopwatch.StartNew();
    readonly System.Windows.Forms.Timer tm = new() { Interval = 16 };
    RectangleF imgRect; readonly RectangleF[] btn = new RectangleF[3];
    int hover = -1; bool drag;

    static readonly Font fTitle = new("Segoe UI Light", 30f), fSub = new("Segoe UI", 11f), fBold = new("Segoe UI Semibold", 11f), fIco = new("Segoe MDL2 Assets", 15f);
    static readonly StringFormat ctr = new() { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center };
    static readonly Color[] pal = { Color.FromArgb(255, 80, 120), Color.FromArgb(90, 120, 255), Color.FromArgb(60, 220, 200), Color.FromArgb(180, 90, 255) };

    public Viewer()
    {
        Text = "PhoneCast"; ClientSize = new Size(420, 860); MinimumSize = new Size(360, 600);
        BackColor = Color.FromArgb(10, 12, 28); DoubleBuffered = true; ResizeRedraw = true;
        qr = MakeQr();
        tm.Tick += (_, _) =>
        {
            double n = sw.Elapsed.TotalSeconds; float dt = (float)(n - last); last = n; t = (float)n;
            live += (target - live) * Math.Min(1f, dt * 6f); Invalidate();
        };
        tm.Start();
        Shown += (_, _) => new Thread(Listen) { IsBackground = true }.Start();
    }

    protected override void OnHandleCreated(EventArgs e)
    {
        base.OnHandleCreated(e);
        int on = 1, cap = 0x001C0C0A;
        DwmSetWindowAttribute(Handle, 20, ref on, 4);
        DwmSetWindowAttribute(Handle, 35, ref cap, 4);
    }

    static string Tok() => RandomNumberGenerator.GetInt32(100000, 1000000).ToString();

    static string LocalIp()
    {
        foreach (var n in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (n.OperationalStatus != OperationalStatus.Up || n.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
            var pr = n.GetIPProperties();
            if (!pr.GatewayAddresses.Any(g => g.Address.AddressFamily == AddressFamily.InterNetwork && !g.Address.Equals(IPAddress.Any))) continue;
            var a = pr.UnicastAddresses.FirstOrDefault(u => u.Address.AddressFamily == AddressFamily.InterNetwork);
            if (a != null) return a.Address.ToString();
        }
        return "127.0.0.1";
    }

    Bitmap MakeQr()
    {
        var gen = new QRCodeGenerator();
        var data = gen.CreateQrCode($"PHC|{ip}|{Port}|{token}", QRCodeGenerator.ECCLevel.M);
        return new QRCode(data).GetGraphic(12, Color.FromArgb(15, 18, 40), Color.White, false);
    }

    void Listen()
    {
        TcpListener l;
        try { l = new TcpListener(IPAddress.Any, Port); l.Start(); }
        catch { BeginInvoke(new Action(() => MessageBox.Show("Port 5555 jest zajęty."))); return; }
        while (true)
        {
            bool authed = false;
            try
            {
                using var c = l.AcceptTcpClient();
                c.NoDelay = true; c.ReceiveTimeout = 5000;
                var s = c.GetStream(); var sb = new StringBuilder(); int b;
                while ((b = s.ReadByte()) != '\n' && b != -1 && sb.Length < 16) sb.Append((char)b);
                if (sb.ToString() != token) continue;
                c.ReceiveTimeout = 0; cli = c; ns = s; target = 1; authed = true;
                var br = new BinaryReader(s);
                while (true)
                {
                    int len = IPAddress.NetworkToHostOrder(br.ReadInt32());
                    var bm = new Bitmap(new MemoryStream(br.ReadBytes(len)));
                    BeginInvoke(new Action(() => { var o = frame; frame = bm; o?.Dispose(); }));
                }
            }
            catch { }
            ns = null; cli = null; target = 0;
            if (authed) { token = Tok(); BeginInvoke(new Action(() => { var o = qr; qr = MakeQr(); o?.Dispose(); })); }
        }
    }

    // ---------- wejście ----------
    void Send(string c, Point? p = null)
    {
        var s = ns; if (s == null) return;
        if (p is Point q)
        {
            float x = Math.Clamp((q.X - imgRect.X) / imgRect.Width, 0f, 1f), y = Math.Clamp((q.Y - imgRect.Y) / imgRect.Height, 0f, 1f);
            c += " " + x.ToString("F4", CultureInfo.InvariantCulture) + " " + y.ToString("F4", CultureInfo.InvariantCulture);
        }
        try { lock (s) s.Write(Encoding.ASCII.GetBytes(c + "\n")); } catch { }
    }

    protected override void OnMouseDown(MouseEventArgs e)
    {
        base.OnMouseDown(e);
        if (ns == null) return;
        if (e.Button == MouseButtons.Left)
        {
            for (int i = 0; i < 3; i++)
                if (btn[i].Contains(e.Location)) { if (i == 0) Send("B"); else if (i == 1) Send("H"); else cli?.Close(); return; }
            if (imgRect.Contains(e.Location)) { drag = true; Send("D", e.Location); }
        }
        else if (imgRect.Contains(e.Location)) Send(e.Button == MouseButtons.Right ? "B" : "H");
    }
    protected override void OnMouseUp(MouseEventArgs e)
    {
        base.OnMouseUp(e);
        if (e.Button == MouseButtons.Left && drag) { drag = false; Send("U", e.Location); }
    }
    protected override void OnMouseWheel(MouseEventArgs e) { base.OnMouseWheel(e); Send("W " + (e.Delta > 0 ? 1 : -1)); }
    protected override void OnMouseMove(MouseEventArgs e)
    {
        base.OnMouseMove(e); hover = -1;
        if (ns != null) for (int i = 0; i < 3; i++) if (btn[i].Contains(e.Location)) hover = i;
        Cursor = hover >= 0 ? Cursors.Hand : Cursors.Default;
    }

    // ---------- Liquid Glass ----------
    static Color Wc(int a, float k) => Color.FromArgb(Math.Clamp((int)(a * k), 0, 255), 255, 255, 255);

    static GraphicsPath RR(RectangleF r, float rad)
    {
        float d = Math.Min(rad * 2, Math.Min(r.Width, r.Height)); var p = new GraphicsPath();
        p.AddArc(r.X, r.Y, d, d, 180, 90); p.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        p.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90); p.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        p.CloseFigure(); return p;
    }

    static void Glass(Graphics g, RectangleF r, float rad, float k)
    {
        if (k <= .01f) return;
        using var p = RR(r, rad);
        using (var b = new LinearGradientBrush(r, Wc(64, k), Wc(16, k), 90f)) g.FillPath(b, p);
        using (var sb = new LinearGradientBrush(r, Wc(210, k), Wc(30, k), 45f))
        using (var pen = new Pen(sb, 1.6f)) g.DrawPath(pen, p);
        var hr = new RectangleF(r.X + rad * .5f, r.Y + 2, r.Width - rad, Math.Max(4, r.Height * .3f));
        using var hp = RR(hr, rad * .5f);
        using var hb = new LinearGradientBrush(hr, Wc(70, k), Wc(0, k), 90f);
        g.FillPath(hb, hp);
    }

    static void Txt(Graphics g, string s, Font f, RectangleF r, Color c) { using var b = new SolidBrush(c); g.DrawString(s, f, b, r, ctr); }

    void Blobs(Graphics g, int W, int H)
    {
        int bw = Math.Max(8, W / 12), bh = Math.Max(8, H / 12);
        if (blobs == null || blobs.Width != bw || blobs.Height != bh) { blobs?.Dispose(); blobs = new Bitmap(bw, bh); }
        using (var gb = Graphics.FromImage(blobs))
        {
            gb.Clear(Color.FromArgb(10, 12, 28)); gb.SmoothingMode = SmoothingMode.AntiAlias;
            for (int i = 0; i < pal.Length; i++)
            {
                float a = t * (.25f + .07f * i) + i * 1.7f, r = bh * (.16f + .03f * i);
                float x = bw * (.5f + .38f * MathF.Sin(a)), y = bh * (.5f + .38f * MathF.Cos(a * .8f + i));
                using var br = new SolidBrush(Color.FromArgb(170, pal[i])); gb.FillEllipse(br, x - r, y - r, 2 * r, 2 * r);
            }
        }
        g.InterpolationMode = InterpolationMode.HighQualityBicubic;
        g.DrawImage(blobs, 0, 0, W, H);
    }

    protected override void OnPaintBackground(PaintEventArgs e) { }

    protected override void OnPaint(PaintEventArgs e)
    {
        var g = e.Graphics; int W = ClientSize.Width, H = ClientSize.Height;
        if (W < 20 || H < 20) return;
        g.SmoothingMode = SmoothingMode.AntiAlias; g.TextRenderingHint = TextRenderingHint.AntiAliasGridFit; g.PixelOffsetMode = PixelOffsetMode.HighQuality;
        Blobs(g, W, H);
        float ease = live * live * (3 - 2 * live), q = 1 - ease; bool on = live > .5f;

        var pill = new RectangleF(W / 2f - 105, 18, 210, 38);
        Glass(g, pill, 19, 1);
        using (var db = new SolidBrush(Color.FromArgb((int)(255 * (.55f + .45f * MathF.Sin(t * 3))), on ? Color.FromArgb(70, 230, 140) : Color.FromArgb(255, 190, 70))))
            g.FillEllipse(db, pill.X + 20, pill.Y + 14, 10, 10);
        Txt(g, on ? "Na żywo" : "Oczekiwanie na telefon", fBold, new RectangleF(pill.X + 26, pill.Y, pill.Width - 26, pill.Height), Color.White);

        if (q > .01f)
        {
            float cw = Math.Min(W - 64, 340f), cx = (W - cw) / 2, cy = (H - (cw + 118)) / 2 + 20 + (1 - q) * 50 + MathF.Sin(t * 1.3f) * 4;
            Glass(g, new RectangleF(cx, cy, cw, cw + 118), 36, q);
            Txt(g, "PhoneCast", fTitle, new RectangleF(cx, cy + 14, cw, 60), Wc(255, q));
            var box = new RectangleF(cx + 36, cy + 84, cw - 72, cw - 72);
            using (var bp = RR(box, 22)) using (var bb = new SolidBrush(Wc(255, q))) g.FillPath(bb, bp);
            if (qr != null)
            {
                using var ia = new ImageAttributes(); ia.SetColorMatrix(new ColorMatrix { Matrix33 = q });
                g.DrawImage(qr, Rectangle.Round(RectangleF.Inflate(box, -14, -14)), 0, 0, qr.Width, qr.Height, GraphicsUnit.Pixel, ia);
            }
            Txt(g, "Otwórz PhoneCast w telefonie\ni zeskanuj ten kod", fSub, new RectangleF(cx, cy + cw + 20, cw, 50), Wc(210, q));
            Txt(g, ip + " : " + Port, fSub, new RectangleF(cx, cy + cw + 74, cw, 24), Wc(110, q));
        }

        if (live > .01f)
        {
            if (frame != null)
            {
                float aw = W - 48, ah = H - 74 - 100;
                float k = Math.Min(aw / frame.Width, ah / frame.Height) * (.9f + .1f * ease);
                float iw = frame.Width * k, ih = frame.Height * k;
                imgRect = new RectangleF((W - iw) / 2, 74 + (ah - ih) / 2, iw, ih);
                Glass(g, RectangleF.Inflate(imgRect, 9, 9), 38, ease);
                using var fp = RR(imgRect, 30);
                g.SetClip(fp); g.DrawImage(frame, imgRect); g.ResetClip();
                using (var fb = new SolidBrush(Color.FromArgb((int)(255 * q), 10, 12, 28))) g.FillPath(fb, fp);
                using (var fpen = new Pen(Wc(90, ease), 2f)) g.DrawPath(fpen, fp);
            }
            var bar = new RectangleF((W - 236) / 2f, H - 80 + (1 - ease) * 70, 236, 60);
            Glass(g, bar, 30, ease);
            string[] ic = { "\uE72B", "\uE80F", "\uE7E8" };
            for (int i = 0; i < 3; i++)
            {
                btn[i] = new RectangleF(bar.X + 20 + i * 70, bar.Y + 2, 56, 56);
                if (hover == i) Glass(g, RectangleF.Inflate(btn[i], -4, -4), 24, ease);
                Txt(g, ic[i], fIco, btn[i], i == 2 ? Color.FromArgb((int)(255 * ease), 255, 110, 110) : Wc(240, ease));
            }
        }
    }
}
