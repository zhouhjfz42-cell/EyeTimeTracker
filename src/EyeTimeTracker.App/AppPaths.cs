namespace EyeTimeTracker.App;

public static class AppPaths
{
    public static string StateFilePath { get; } = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "EyeTimeTracker",
        "state.json");
}
