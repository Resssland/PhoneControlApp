import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.W32APIOptions;

import static com.sun.jna.platform.win32.WinGDI.DIB_RGB_COLORS;

public class Jna {

    public static void /*BufferedImage*/ getScreenshot(Rectangle bounds) {

        WinDef.HDC windowDC = GDI.GetDC(USER.GetDesktopWindow());
        WinDef.HBITMAP outputBitmap =
                GDI.CreateCompatibleBitmap(windowDC,
                        bounds.width, bounds.height);
        try {
            WinDef.HDC blitDC = GDI.CreateCompatibleDC(windowDC);
            try {
                WinNT.HANDLE oldBitmap =
                        GDI.SelectObject(blitDC, outputBitmap);
                try {
                    GDI.BitBlt(blitDC,
                            0, 0, bounds.width, bounds.height,
                            windowDC,
                            bounds.x, bounds.y,
                            GDI32.SRCCOPY);
                } finally {
                    GDI.SelectObject(blitDC, oldBitmap);
                }
                WinGDI.BITMAPINFO bi = new WinGDI.BITMAPINFO(40);
                bi.bmiHeader.biSize = 40;
                boolean ok =
                        GDI.GetDIBits(blitDC, outputBitmap, 0, bounds.height,
                                (byte[]) null, bi, DIB_RGB_COLORS);
/*
                if (ok) {
                    WinGDI.BITMAPINFOHEADER bih = bi.bmiHeader;
                    bih.biHeight = - Math.abs(bih.biHeight);
                    bi.bmiHeader.biCompression = 0;
                    return bufferedImageFromBitmap(blitDC, outputBitmap, bi);
                } else {
                    return null;
                }
*/
            } finally {
                GDI.DeleteObject(blitDC);
            }
        } finally {
            GDI.DeleteObject(outputBitmap);
        }
    }

    private static BufferedImage
    bufferedImageFromBitmap(WinDef.HDC blitDC,
                            WinDef.HBITMAP outputBitmap,
                            WinGDI.BITMAPINFO bi) {
        WinGDI.BITMAPINFOHEADER bih = bi.bmiHeader;
        int height = Math.abs(bih.biHeight);
        final ColorModel cm;
        final DataBuffer buffer;
        final WritableRaster raster;
        int strideBits =
                (bih.biWidth * bih.biBitCount);
        int strideBytesAligned =
                (((strideBits - 1) | 0x1F) + 1) >> 3;
        final int strideElementsAligned;
        switch (bih.biBitCount) {
            case 16:
                strideElementsAligned = strideBytesAligned / 2;
                cm = new DirectColorModel(16, 0x7C00, 0x3E0, 0x1F);
                buffer =
                        new DataBufferUShort(strideElementsAligned * height);
                raster =
                        Raster.createPackedRaster(buffer,
                                bih.biWidth, height,
                                strideElementsAligned,
                                ((DirectColorModel) cm).getMasks(),
                                null);
                break;
            case 32:
                strideElementsAligned = strideBytesAligned / 4;
                cm = new DirectColorModel(32, 0xFF0000, 0xFF00, 0xFF);
                buffer =
                        new DataBufferInt(strideElementsAligned * height);
                raster =
                        Raster.createPackedRaster(buffer,
                                bih.biWidth, height,
                                strideElementsAligned,
                                ((DirectColorModel) cm).getMasks(),
                                null);
                break;
            default:
                throw new IllegalArgumentException("Unsupported bit count: " + bih.biBitCount);
        }
        final boolean ok;
        switch (buffer.getDataType()) {
            case DataBuffer.TYPE_INT:
            {
                int[] pixels = ((DataBufferInt) buffer).getData();
                ok = GDI.GetDIBits(blitDC, outputBitmap, 0, raster.getHeight(), pixels, bi, 0);
            }
            break;
            case DataBuffer.TYPE_USHORT:
            {
                short[] pixels = ((DataBufferUShort) buffer).getData();
                ok = GDI.GetDIBits(blitDC, outputBitmap, 0, raster.getHeight(), pixels, bi, 0);
            }
            break;
            default:
                throw new AssertionError("Unexpected buffer element type: " + buffer.getDataType());
        }

        if (ok) {
            return new BufferedImage(cm, raster, false, null);
        } else {
            return null;
        }
    }

    private static final User32 USER = User32.INSTANCE;

    private static final GDI32 GDI = GDI32.INSTANCE;

}

interface GDI32 extends com.sun.jna.platform.win32.GDI32 {
    GDI32 INSTANCE =
            (GDI32) Native.loadLibrary(GDI32.class);
    boolean BitBlt(WinDef.HDC hdcDest,
                   int nXDest,
                   int nYDest,
                   int nWidth,
                   int nHeight,
                   WinDef.HDC hdcSrc,
                   int nXSrc,
                   int nYSrc,
                   int dwRop);
    WinDef.HDC GetDC(WinDef.HWND hWnd);
    boolean GetDIBits(WinDef.HDC dc, WinDef.HBITMAP bmp, int startScan, int scanLines,
                      byte[] pixels, WinGDI.BITMAPINFO bi, int usage);
    boolean GetDIBits(WinDef.HDC dc, WinDef.HBITMAP bmp, int startScan, int scanLines,
                      short[] pixels, WinGDI.BITMAPINFO bi, int usage);
    boolean GetDIBits(WinDef.HDC dc, WinDef.HBITMAP bmp, int startScan, int scanLines,
                      int[] pixels, WinGDI.BITMAPINFO bi, int usage);
    int SRCCOPY = 0xCC0020;
}

interface User32 extends com.sun.jna.platform.win32.User32 {
    User32 INSTANCE =
            (User32) Native.loadLibrary(User32.class,
                    W32APIOptions.UNICODE_OPTIONS);
    HWND GetDesktopWindow();
}