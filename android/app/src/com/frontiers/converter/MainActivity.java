package com.frontiers.converter;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1;
    private static final int REQ_DIR = 2;
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MIN_SHORT_SIDE = 600;
    // 解码阶段的像素上限，避免手机内存被打爆
    private static final long MAX_DECODE_PIXELS = 14_000_000L;

    private final ArrayList<Uri> inputUris = new ArrayList<Uri>();
    private Uri treeUri;

    private TextView tvFiles;
    private TextView tvDir;
    private TextView tvStatus;
    private Button btnStart;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvFiles = findViewById(R.id.tvFiles);
        tvDir = findViewById(R.id.tvDir);
        tvStatus = findViewById(R.id.tvStatus);
        btnStart = findViewById(R.id.btnStart);
        progress = findViewById(R.id.progress);

        Button btnPick = findViewById(R.id.btnPick);
        btnPick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(Intent.createChooser(i, "选择图片"), REQ_PICK);
            }
        });

        Button btnDir = findViewById(R.id.btnDir);
        btnDir.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                startActivityForResult(i, REQ_DIR);
            }
        });

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startConvert();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        if (requestCode == REQ_PICK) {
            inputUris.clear();
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                for (int i = 0; i < n; i++) {
                    inputUris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                inputUris.add(data.getData());
            }
            tvFiles.setText("已选择 " + inputUris.size() + " 张图片");
        } else if (requestCode == REQ_DIR) {
            treeUri = data.getData();
            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            try {
                getContentResolver().takePersistableUriPermission(treeUri, flags);
            } catch (SecurityException ignored) {
            }
            tvDir.setText("输出目录已设置");
        }
    }

    private void startConvert() {
        if (inputUris.isEmpty()) {
            tvStatus.setText("请先选择图片");
            return;
        }
        if (treeUri == null) {
            tvStatus.setText("请先选择输出目录");
            return;
        }
        btnStart.setEnabled(false);
        progress.setMax(inputUris.size());
        progress.setProgress(0);

        final List<Uri> jobs = new ArrayList<Uri>(inputUris);
        new Thread(new Runnable() {
            @Override
            public void run() {
                int done = 0;
                int failed = 0;
                for (Uri uri : jobs) {
                    try {
                        String name = convertOne(uri);
                        postStatus("已完成：" + name);
                    } catch (Throwable t) {
                        failed++;
                        // 带上异常类型，方便定位
                        String msg = t.getClass().getSimpleName();
                        if (t.getMessage() != null) {
                            msg += "：" + t.getMessage();
                        }
                        postStatus("失败：" + msg);
                    }
                    done++;
                    final int p = done;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setProgress(p);
                        }
                    });
                }
                final int f = failed;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        btnStart.setEnabled(true);
                        String msg = "全部结束，共 " + jobs.size() + " 张";
                        if (f > 0) {
                            msg += "，失败 " + f + " 张";
                        }
                        tvStatus.setText(msg);
                    }
                });
            }
        }).start();
    }

    private void postStatus(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tvStatus.setText(s);
            }
        });
    }

    private String convertOne(Uri uri) throws Exception {
        // 复制到缓存，便于解码和读取 EXIF
        File tmp = File.createTempFile("src_", ".img", getCacheDir());
        InputStream in = getContentResolver().openInputStream(uri);
        FileOutputStream copy = new FileOutputStream(tmp);
        copy(in, copy);
        copy.close();
        in.close();

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(tmp.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw new Exception("系统不支持该图片格式（可能是 TIFF），请先转成 PNG/JPG");
        }

        // 先按像素上限选采样率，再留内存失败时的加倍兜底
        int sample = 1;
        long pixels = (long) bounds.outWidth * bounds.outHeight;
        while (pixels / ((long) sample * sample) > MAX_DECODE_PIXELS) {
            sample *= 2;
        }

        Bitmap bmp = null;
        int guard = 0;
        while (bmp == null) {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = sample;
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try {
                bmp = BitmapFactory.decodeFile(tmp.getAbsolutePath(), opt);
            } catch (OutOfMemoryError oom) {
                bmp = null;
            }
            if (bmp == null) {
                sample *= 2;
            }
            guard++;
            if (guard > 7) {
                throw new Exception("无法解码该图片，请换一张或缩小后再试");
            }
        }

        int rotation = 0;
        try {
            ExifInterface exif = new ExifInterface(tmp.getAbsolutePath());
            int ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) {
                rotation = 90;
            } else if (ori == ExifInterface.ORIENTATION_ROTATE_180) {
                rotation = 180;
            } else if (ori == ExifInterface.ORIENTATION_ROTATE_270) {
                rotation = 270;
            }
        } catch (Exception ignored) {
        }
        if (rotation != 0) {
            Matrix m = new Matrix();
            m.postRotate(rotation);
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(),
                    bmp.getHeight(), m, true);
            if (rotated != bmp) {
                bmp.recycle();
                bmp = rotated;
            }
        }

        byte[] tiff = null;
        while (true) {
            byte[] rgb = flattenRgb(bmp);
            tiff = TiffWriter.write(bmp.getWidth(), bmp.getHeight(), rgb);
            int shortSide = Math.min(bmp.getWidth(), bmp.getHeight());
            if (tiff.length <= MAX_BYTES || shortSide <= MIN_SHORT_SIDE) {
                break;
            }
            int nw = Math.max(1, Math.round(bmp.getWidth() * 0.9f));
            int nh = Math.max(1, Math.round(bmp.getHeight() * 0.9f));
            Bitmap scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true);
            if (scaled != bmp) {
                bmp.recycle();
                bmp = scaled;
            }
        }

        String base = queryName(uri);
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }

        // 用树文档 URI 创建输出文件，直接传 treeUri 在部分机型上会失败
        String parentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId);
        Uri outUri = DocumentsContract.createDocument(getContentResolver(),
                parentUri, "image/tiff", base + ".tif");
        if (outUri == null) {
            throw new Exception("无法在输出目录创建文件，请检查目录权限");
        }
        OutputStream os = getContentResolver().openOutputStream(outUri);
        os.write(tiff);
        os.close();
        tmp.delete();
        bmp.recycle();
        return base + ".tif";
    }

    private String queryName(Uri uri) {
        String result = "image";
        android.database.Cursor c = getContentResolver().query(uri,
                new String[]{"_display_name"}, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    String s = c.getString(0);
                    if (s != null) {
                        result = s;
                    }
                }
            } finally {
                c.close();
            }
        }
        return result;
    }

    // 直接从位图取像素，透明像素按白底处理，不再额外分配同尺寸位图
    private byte[] flattenRgb(Bitmap src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getPixels(pixels, 0, w, 0, 0, w, h);
        byte[] rgb = new byte[w * h * 3];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >>> 24) & 0xff;
            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;
            if (a == 255) {
                // 常见情况：不透明
            } else if (a == 0) {
                r = g = b = 255;
            } else {
                r = (r * a + 255 * (255 - a)) / 255;
                g = (g * a + 255 * (255 - a)) / 255;
                b = (b * a + 255 * (255 - a)) / 255;
            }
            rgb[i * 3] = (byte) r;
            rgb[i * 3 + 1] = (byte) g;
            rgb[i * 3 + 2] = (byte) b;
        }
        return rgb;
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }
}
