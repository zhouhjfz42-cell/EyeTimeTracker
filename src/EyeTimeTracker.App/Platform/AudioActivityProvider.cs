using System.Runtime.InteropServices;

namespace EyeTimeTracker.App.Platform;

public sealed class AudioActivityProvider
{
    private const float ActivePeakThreshold = 0.01f;

    public bool IsAudioActive()
    {
        object? enumeratorObject = null;
        IMMDevice? device = null;
        object? meterObject = null;

        try
        {
            enumeratorObject = new MMDeviceEnumerator();
            var enumerator = (IMMDeviceEnumerator)enumeratorObject;

            Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(
                EDataFlow.Render,
                ERole.Multimedia,
                out device));

            var meterId = typeof(IAudioMeterInformation).GUID;
            Marshal.ThrowExceptionForHR(device.Activate(
                ref meterId,
                ClsCtx.All,
                IntPtr.Zero,
                out meterObject));

            var meter = (IAudioMeterInformation)meterObject;
            Marshal.ThrowExceptionForHR(meter.GetPeakValue(out var peak));
            return peak >= ActivePeakThreshold;
        }
        catch (Exception)
        {
            return false;
        }
        finally
        {
            ReleaseComObject(meterObject);
            ReleaseComObject(device);
            ReleaseComObject(enumeratorObject);
        }
    }

    private static void ReleaseComObject(object? comObject)
    {
        if (comObject is not null && Marshal.IsComObject(comObject))
        {
            Marshal.FinalReleaseComObject(comObject);
        }
    }

    [ComImport]
    [Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private sealed class MMDeviceEnumerator
    {
    }

    [ComImport]
    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        [PreserveSig]
        int EnumAudioEndpoints(EDataFlow dataFlow, uint dwStateMask, out object ppDevices);

        [PreserveSig]
        int GetDefaultAudioEndpoint(EDataFlow dataFlow, ERole role, out IMMDevice ppEndpoint);

        [PreserveSig]
        int GetDevice([MarshalAs(UnmanagedType.LPWStr)] string pwstrId, out IMMDevice ppDevice);

        [PreserveSig]
        int RegisterEndpointNotificationCallback(IntPtr pClient);

        [PreserveSig]
        int UnregisterEndpointNotificationCallback(IntPtr pClient);
    }

    [ComImport]
    [Guid("D666063F-1587-4E43-81F1-B948E807363F")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        [PreserveSig]
        int Activate(
            ref Guid iid,
            ClsCtx dwClsCtx,
            IntPtr pActivationParams,
            [MarshalAs(UnmanagedType.Interface)] out object ppInterface);

        [PreserveSig]
        int OpenPropertyStore(uint stgmAccess, out object ppProperties);

        [PreserveSig]
        int GetId([MarshalAs(UnmanagedType.LPWStr)] out string ppstrId);

        [PreserveSig]
        int GetState(out uint pdwState);
    }

    [ComImport]
    [Guid("C02216F6-8C67-4B5B-9D00-D008E73E0064")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioMeterInformation
    {
        [PreserveSig]
        int GetPeakValue(out float pfPeak);

        [PreserveSig]
        int GetMeteringChannelCount(out uint pnChannelCount);

        [PreserveSig]
        int GetChannelsPeakValues(uint u32ChannelCount, [Out] float[] afPeakValues);

        [PreserveSig]
        int QueryHardwareSupport(out uint pdwHardwareSupportMask);
    }

    private enum EDataFlow
    {
        Render = 0,
        Capture = 1,
        All = 2
    }

    private enum ERole
    {
        Console = 0,
        Multimedia = 1,
        Communications = 2
    }

    [Flags]
    private enum ClsCtx : uint
    {
        InprocServer = 0x1,
        InprocHandler = 0x2,
        LocalServer = 0x4,
        RemoteServer = 0x10,
        All = InprocServer | InprocHandler | LocalServer | RemoteServer
    }
}
