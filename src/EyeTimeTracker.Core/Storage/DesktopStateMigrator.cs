using EyeTimeTracker.Core.DesktopActivity;

namespace EyeTimeTracker.Core.Storage;

/// <summary>
/// 旧数据迁移：第一次启动先备份旧 state.json，写 migrationVersion；
/// 迁移可重复执行但不会重复导入。旧数据只保留为 LegacyScreenUsage，
/// 没有桌型／证据时，不能补造 SedentaryBreak 或中断率。
/// </summary>
public static class DesktopStateMigrator
{
    public const string LegacyBackupFileName = "state.json.legacy-backup";

    /// <summary>
    /// 确保旧状态已备份。返回是否执行了本次备份。
    /// 幂等：desktop-state.json 已存在或备份已存在时不再重复。
    /// </summary>
    public static bool EnsureLegacyBackup(string legacyStatePath, string desktopStatePath)
    {
        if (File.Exists(desktopStatePath))
        {
            return false;
        }

        if (!File.Exists(legacyStatePath))
        {
            return false;
        }

        var directory = Path.GetDirectoryName(legacyStatePath);
        var backupPath = Path.Combine(
            string.IsNullOrEmpty(directory) ? "." : directory,
            LegacyBackupFileName);
        if (File.Exists(backupPath))
        {
            return false;
        }

        File.Copy(legacyStatePath, backupPath, overwrite: false);
        return true;
    }
}
