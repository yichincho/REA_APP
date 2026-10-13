package tw.rea.apkbackup;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final int PICK_DIR = 101;
    private final List<Item> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout rows;
    private EditText search;
    private TextView status;
    private Button exportButton;
    private boolean busy = false;

    private static class Item {
        String pkg;
        String label;
        ApplicationInfo info;
        Item(String p, String l, ApplicationInfo i) { pkg=p; label=l; info=i; }
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String content, int size) {
        TextView t = new TextView(this); t.setText(content); t.setTextSize(size); t.setPadding(dp(12), dp(8), dp(12), dp(8)); return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(12), dp(8), dp(8)); setContentView(root);
        root.addView(text("REA APK Backup", 24));
        root.addView(text("匯出已安裝的 APK 與 split APK；不含 App 資料或付費授權。", 13));
        search = new EditText(this); search.setSingleLine(true); search.setHint("搜尋 App 名稱或套件 ID"); root.addView(search);
        LinearLayout bar = new LinearLayout(this); bar.setOrientation(LinearLayout.HORIZONTAL);
        Button all = new Button(this); all.setText("選取顯示項目"); bar.addView(all, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button clear = new Button(this); clear.setText("清除選取"); bar.addView(clear, new LinearLayout.LayoutParams(0, dp(50), 1)); root.addView(bar);
        exportButton = new Button(this); exportButton.setText("匯出所選 App (.apks)"); root.addView(exportButton);
        status = text("載入中…", 13); root.addView(status);
        ScrollView scroll = new ScrollView(this); rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            public void afterTextChanged(Editable e) {}
        });
        all.setOnClickListener(v -> { for(Item i : filtered()) selected.add(i.pkg); render(); });
        clear.setOnClickListener(v -> { selected.clear(); render(); });
        exportButton.setOnClickListener(v -> {
            if(selected.isEmpty()) { Toast.makeText(this, "請先選取 App", Toast.LENGTH_SHORT).show(); return; }
            Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            in.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(in, PICK_DIR);
        });
        loadApps();
    }
    private void loadApps() {
        PackageManager pm = getPackageManager(); items.clear();
        for(ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0 && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0) continue;
            items.add(new Item(ai.packageName, String.valueOf(pm.getApplicationLabel(ai)), ai));
        }
        Collections.sort(items, Comparator.comparing(i -> i.label.toLowerCase(Locale.ROOT)));
        render();
    }
    private List<Item> filtered() {
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Item> found = new ArrayList<>();
        for(Item i : items) if(i.label.toLowerCase(Locale.ROOT).contains(q) || i.pkg.toLowerCase(Locale.ROOT).contains(q)) found.add(i);
        return found;
    }
    private void render() {
        if(rows == null || search == null) return;
        rows.removeAllViews();
        List<Item> found = filtered();
        for(Item i : found) {
            CheckBox cb = new CheckBox(this); cb.setText(i.label + "\n" + i.pkg); cb.setTextSize(14);
            cb.setPadding(dp(4), dp(5), dp(4), dp(5)); cb.setChecked(selected.contains(i.pkg));
            cb.setOnCheckedChangeListener((button, checked) -> { if(checked) selected.add(i.pkg); else selected.remove(i.pkg); updateStatus(); });
            rows.addView(cb);
        }
        updateStatus();
    }
    private void updateStatus() {
        if(!busy) status.setText("顯示 " + filtered().size() + " 個 App；已選 " + selected.size() + " 個");
        exportButton.setEnabled(!busy && !selected.isEmpty());
    }
    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if(req != PICK_DIR || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri tree = data.getData();
        try { getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); }
        catch(Exception ignored) { /* Some providers grant only temporary access. */ }
        List<Item> snapshot = new ArrayList<>();
        for(Item i : items) if(selected.contains(i.pkg)) snapshot.add(i);
        busy=true; updateStatus();
        new Thread(() -> backup(tree, snapshot)).start();
    }
    private void showProgress(String msg) { ui.post(() -> status.setText(msg)); }
    private void backup(Uri tree, List<Item> snapshot) {
        int success=0; List<String> errors=new ArrayList<>();
        String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        for(int idx=0; idx<snapshot.size(); idx++) {
            Item item=snapshot.get(idx);
            showProgress("備份 " + (idx+1) + "/" + snapshot.size() + "：" + item.label);
            Uri output=null;
            try {
                PackageManager pm=getPackageManager();
                ApplicationInfo ai=pm.getApplicationInfo(item.pkg, 0);
                ArrayList<String> paths=new ArrayList<>();
                paths.add(ai.sourceDir);
                if(ai.splitSourceDirs != null) Collections.addAll(paths, ai.splitSourceDirs);
                String filename=item.pkg + "_" + stamp + ".apks";
                Uri rootDoc=DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
                output=DocumentsContract.createDocument(getContentResolver(), rootDoc, "application/zip", filename);
                if(output == null) throw new IllegalStateException("無法建立備份檔");
                try(OutputStream out=getContentResolver().openOutputStream(output, "w")) {
                    if(out == null) throw new IllegalStateException("無法寫入備份檔");
                    try(ZipOutputStream zip = new ZipOutputStream(out)) {
                        byte[] buffer=new byte[64*1024];
                        for(String path : paths) {
                            File file=new File(path);
                            ZipEntry entry = new ZipEntry(file.getName());
                            zip.putNextEntry(entry);
                            try(FileInputStream fis=new FileInputStream(file)) {
                                int n; while((n=fis.read(buffer))!=-1) zip.write(buffer,0,n);
                            }
                            zip.closeEntry();
                        }
                        PackageInfo pi=pm.getPackageInfo(item.pkg, 0);
                        String meta="Package: " + item.pkg + "\nLabel: " + item.label + "\nVersion: " + pi.versionName + "\nAPK count: " + paths.size() + "\nExported: " + stamp + "\nImportant: This does not include app data or licenses.\n";
                        zip.putNextEntry(new ZipEntry("backup-info.txt"));
                        zip.write(meta.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                    }
                }
                success++;
            } catch(Exception ex) {
                if(output != null) try { DocumentsContract.deleteDocument(getContentResolver(), output); } catch(Exception ignored) {}
                errors.add(item.pkg + ": " + ex.getMessage());
            }
        }
        int good=success;
        ui.post(() -> {
            busy=false; updateStatus();
            String msg="成功備份 " + good + "/" + snapshot.size() + " 個 App。";
            if(!errors.isEmpty()) msg += "\n\n失敗：\n" + android.text.TextUtils.join("\n", errors);
            new AlertDialog.Builder(this).setTitle("備份結果").setMessage(msg).setPositiveButton("確定",null).show();
        });
    }
}
