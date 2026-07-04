using System.Drawing;

namespace EyeTimeTracker.App.UI;

public static class MainFormLayout
{
    public static Rectangle TitleBounds { get; } = new(34, 30, 270, 76);

    public static Rectangle SubtitleBounds { get; } = new(34, 92, 410, 50);

    public static Rectangle StartupLabelBounds { get; } = new(424, 40, 126, 34);

    public static Rectangle StartupSwitchBounds { get; } = new(558, 43, 52, 28);

    public static Rectangle PairingButtonBounds { get; } = new(500, 154, 110, 34);
}
