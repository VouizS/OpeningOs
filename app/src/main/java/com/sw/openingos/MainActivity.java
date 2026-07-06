package com.sw.openingos;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private OpeningView openingView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
        openingView = new OpeningView(this);
        setContentView(openingView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openingView != null) openingView.refreshApps();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (openingView != null && openingView.handleKey(keyCode)) return true;
        return super.onKeyDown(keyCode, event);
    }

    static class LauncherApp {
        final String label;
        final String packageName;
        final Intent launchIntent;

        LauncherApp(String label, String packageName, Intent launchIntent) {
            this.label = label;
            this.packageName = packageName;
            this.launchIntent = launchIntent;
        }
    }

    static class OpeningView extends View {
        private final Activity activity;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final SharedPreferences prefs;

        private final String[] tabs = {"TV", "APPS", "INTERNET", "ARQUIVOS", "AMBIENTE", "CONFIG"};
        private int selectedTab = 0;
        private int selectedRow = 0;
        private long lastInputAt = System.currentTimeMillis();
        private boolean ambientPreview = false;

        private final ArrayList<LauncherApp> installedApps = new ArrayList<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd/MM", Locale.getDefault());

        OpeningView(Activity activity) {
            super(activity);
            this.activity = activity;
            this.prefs = activity.getSharedPreferences("opening_os", Context.MODE_PRIVATE);
            setFocusable(true);
            setFocusableInTouchMode(true);
            refreshApps();
        }

        void refreshApps() {
            installedApps.clear();
            PackageManager pm = activity.getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN, null);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);
            for (ResolveInfo info : resolved) {
                String pkg = info.activityInfo.packageName;
                if (pkg.equals(activity.getPackageName())) continue;
                String label = info.loadLabel(pm).toString();
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    installedApps.add(new LauncherApp(label, pkg, launch));
                }
            }
            Collections.sort(installedApps, new Comparator<LauncherApp>() {
                @Override
                public int compare(LauncherApp a, LauncherApp b) {
                    return a.label.compareToIgnoreCase(b.label);
                }
            });
            invalidate();
        }

        boolean handleKey(int keyCode) {
            lastInputAt = System.currentTimeMillis();
            ambientPreview = false;
            int maxRows = getRowCountForTab();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    selectedTab = Math.max(0, selectedTab - 1);
                    selectedRow = 0;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    selectedTab = Math.min(tabs.length - 1, selectedTab + 1);
                    selectedRow = 0;
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    selectedRow = Math.max(0, selectedRow - 1);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    selectedRow = Math.min(Math.max(0, maxRows - 1), selectedRow + 1);
                    invalidate();
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    activateSelected();
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (selectedTab != 0) {
                        selectedTab = 0;
                        selectedRow = 0;
                        invalidate();
                        return true;
                    }
                    return false;
            }
            return false;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();

            drawBackground(canvas, w, h);

            if (shouldShowAmbient()) {
                drawAmbient(canvas, w, h, true);
                postInvalidateDelayed(1000);
                return;
            }

            drawTopBar(canvas, w, h);
            drawTabs(canvas, w, h);
            drawSideList(canvas, w, h);
            drawPreview(canvas, w, h);
            drawBottomHints(canvas, w, h);

            postInvalidateDelayed(1000);
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            LinearGradient bg = new LinearGradient(0, 0, w, h,
                    new int[]{Color.rgb(7, 7, 11), Color.rgb(26, 14, 24), Color.rgb(54, 16, 18)},
                    null, Shader.TileMode.CLAMP);
            paint.setShader(bg);
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setColor(Color.argb(70, 255, 61, 77));
            canvas.drawCircle(w * 0.78f, h * 0.22f, h * 0.28f, paint);
            paint.setColor(Color.argb(40, 20, 160, 255));
            canvas.drawCircle(w * 0.20f, h * 0.80f, h * 0.32f, paint);
        }

        private void drawTopBar(Canvas canvas, int w, int h) {
            float pad = dp(28);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(22));
            text.setColor(Color.WHITE);
            canvas.drawText("Opening OS", pad, dp(44), text);

            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(sp(12));
            text.setColor(Color.argb(190, 255, 255, 255));
            canvas.drawText("IPTV Launcher Base • v0.1", pad, dp(64), text);

            String time = timeFormat.format(new Date());
            String date = dateFormat.format(new Date());
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(sp(23));
            text.setColor(Color.WHITE);
            canvas.drawText(time, w - pad, dp(44), text);
            text.setTextSize(sp(12));
            text.setColor(Color.argb(180, 255, 255, 255));
            canvas.drawText(date, w - pad, dp(64), text);
            text.setTextAlign(Paint.Align.LEFT);

            drawPill(canvas, w - dp(245), dp(22), dp(95), dp(34), "Buscar", false);
            drawPill(canvas, w - dp(350), dp(22), dp(92), dp(34), "Filtro", false);
        }

        private void drawTabs(Canvas canvas, int w, int h) {
            float startX = dp(150);
            float y = dp(104);
            float itemW = dp(118);
            for (int i = 0; i < tabs.length; i++) {
                boolean active = i == selectedTab;
                rect.set(startX + itemW * i, y - dp(28), startX + itemW * i + itemW - dp(12), y + dp(16));
                paint.setColor(active ? Color.argb(235, 255, 61, 77) : Color.argb(42, 255, 255, 255));
                canvas.drawRoundRect(rect, dp(18), dp(18), paint);
                text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                text.setTextSize(sp(14));
                text.setColor(active ? Color.WHITE : Color.argb(190, 255, 255, 255));
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(tabs[i], rect.centerX(), y, text);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawSideList(Canvas canvas, int w, int h) {
            float x = dp(28);
            float y = dp(150);
            float width = dp(300);
            float rowH = dp(50);

            rect.set(x, y - dp(36), x + width, h - dp(86));
            paint.setColor(Color.argb(95, 0, 0, 0));
            canvas.drawRoundRect(rect, dp(24), dp(24), paint);

            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(15));
            text.setColor(Color.WHITE);
            canvas.drawText(getSideTitle(), x + dp(22), y, text);

            List<String> rows = getRowsForTab();
            for (int i = 0; i < rows.size(); i++) {
                float ry = y + dp(30) + i * rowH;
                boolean active = i == selectedRow;
                rect.set(x + dp(14), ry, x + width - dp(14), ry + rowH - dp(8));
                paint.setColor(active ? Color.argb(230, 255, 61, 77) : Color.argb(35, 255, 255, 255));
                canvas.drawRoundRect(rect, dp(14), dp(14), paint);

                text.setTypeface(active ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
                text.setTextSize(sp(14));
                text.setColor(active ? Color.WHITE : Color.argb(220, 255, 255, 255));
                canvas.drawText(rows.get(i), rect.left + dp(18), rect.centerY() + dp(5), text);
            }
        }

        private void drawPreview(Canvas canvas, int w, int h) {
            float x = dp(355);
            float y = dp(150);
            float width = w - x - dp(28);
            float height = h - y - dp(92);

            rect.set(x, y, x + width, y + height);
            paint.setColor(Color.argb(105, 255, 255, 255));
            canvas.drawRoundRect(rect, dp(28), dp(28), paint);

            rect.inset(dp(2), dp(2));
            paint.setColor(Color.argb(185, 8, 9, 15));
            canvas.drawRoundRect(rect, dp(26), dp(26), paint);

            String title = getPreviewTitle();
            String subtitle = getPreviewSubtitle();

            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(28));
            text.setColor(Color.WHITE);
            canvas.drawText(title, x + dp(34), y + dp(58), text);

            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(sp(15));
            text.setColor(Color.argb(205, 255, 255, 255));
            drawMultiline(canvas, subtitle, x + dp(34), y + dp(91), width - dp(68), sp(19));

            drawHeroPanel(canvas, x + dp(34), y + dp(145), width - dp(68), height - dp(200));
        }

        private void drawHeroPanel(Canvas canvas, float x, float y, float width, float height) {
            rect.set(x, y, x + width, y + height);
            LinearGradient panel = new LinearGradient(x, y, x + width, y + height,
                    new int[]{Color.rgb(255, 61, 77), Color.rgb(108, 29, 100), Color.rgb(13, 137, 196)},
                    null, Shader.TileMode.CLAMP);
            paint.setShader(panel);
            canvas.drawRoundRect(rect, dp(24), dp(24), paint);
            paint.setShader(null);

            paint.setColor(Color.argb(90, 0, 0, 0));
            canvas.drawRoundRect(rect, dp(24), dp(24), paint);

            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(22));
            text.setColor(Color.WHITE);
            canvas.drawText(getHeroLine(), x + dp(28), y + dp(52), text);

            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(sp(14));
            text.setColor(Color.argb(215, 255, 255, 255));
            canvas.drawText("Use ← → para categorias, ↑ ↓ para itens e OK para abrir.", x + dp(28), y + dp(84), text);

            float tileW = Math.min(dp(180), (width - dp(86)) / 3f);
            float tileY = y + height - dp(88);
            drawSmallTile(canvas, x + dp(28), tileY, tileW, "HOME", "Launcher padrão");
            drawSmallTile(canvas, x + dp(46) + tileW, tileY, tileW, "FLOATING", "Em breve");
            drawSmallTile(canvas, x + dp(64) + tileW * 2, tileY, tileW, "AMBIENTE", "Quadro vivo");
        }

        private void drawSmallTile(Canvas canvas, float x, float y, float w, String title, String sub) {
            rect.set(x, y, x + w, y + dp(58));
            paint.setColor(Color.argb(110, 255, 255, 255));
            canvas.drawRoundRect(rect, dp(16), dp(16), paint);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(12));
            text.setColor(Color.WHITE);
            canvas.drawText(title, x + dp(14), y + dp(23), text);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(sp(10));
            text.setColor(Color.argb(210, 255, 255, 255));
            canvas.drawText(sub, x + dp(14), y + dp(43), text);
        }

        private void drawBottomHints(Canvas canvas, int w, int h) {
            float y = h - dp(48);
            drawPill(canvas, dp(28), y - dp(22), dp(150), dp(36), "OK abrir", false);
            drawPill(canvas, dp(190), y - dp(22), dp(185), dp(36), "← → categorias", false);
            drawPill(canvas, dp(390), y - dp(22), dp(150), dp(36), "↑ ↓ itens", false);

            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(sp(12));
            text.setColor(Color.argb(165, 255, 255, 255));
            canvas.drawText("Opening OS v0.1 • base visual IPTV para projetor", w - dp(28), y, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawAmbient(Canvas canvas, int w, int h, boolean auto) {
            drawBackground(canvas, w, h);
            paint.setColor(Color.argb(135, 0, 0, 0));
            canvas.drawRect(0, 0, w, h, paint);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(40));
            text.setColor(Color.WHITE);
            canvas.drawText(timeFormat.format(new Date()), w / 2f, h / 2f - dp(12), text);

            text.setTypeface(android.graphics.Typeface.DEFAULT);
            text.setTextSize(sp(16));
            text.setColor(Color.argb(210, 255, 255, 255));
            canvas.drawText("Modo Ambiente • paisagens, fotos e vídeos entrarão nas próximas versões", w / 2f, h / 2f + dp(28), text);
            text.setTextSize(sp(12));
            canvas.drawText("Pressione qualquer botão para voltar", w / 2f, h - dp(42), text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawPill(Canvas canvas, float x, float y, float w, float h, String label, boolean active) {
            rect.set(x, y, x + w, y + h);
            paint.setColor(active ? Color.argb(230, 255, 61, 77) : Color.argb(48, 255, 255, 255));
            canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setTextSize(sp(11));
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(label, rect.centerX(), rect.centerY() + dp(4), text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawMultiline(Canvas canvas, String value, float x, float y, float maxWidth, float lineHeight) {
            String[] words = value.split(" ");
            StringBuilder line = new StringBuilder();
            float currentY = y;
            for (String word : words) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (text.measureText(next) > maxWidth && line.length() > 0) {
                    canvas.drawText(line.toString(), x, currentY, text);
                    currentY += lineHeight;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (line.length() > 0) canvas.drawText(line.toString(), x, currentY, text);
        }

        private String getSideTitle() {
            switch (selectedTab) {
                case 0: return "Central de TV";
                case 1: return "Aplicativos";
                case 2: return "Internet";
                case 3: return "Arquivos";
                case 4: return "Modo Ambiente";
                default: return "Configurações";
            }
        }

        private List<String> getRowsForTab() {
            ArrayList<String> rows = new ArrayList<>();
            switch (selectedTab) {
                case 0:
                    Collections.addAll(rows, "HDMI", "YouTube", "UniTV / IPTV", "Modo Cinema", "Floating Layer");
                    break;
                case 1:
                    if (installedApps.isEmpty()) {
                        rows.add("Nenhum app encontrado");
                    } else {
                        int limit = Math.min(9, installedApps.size());
                        for (int i = 0; i < limit; i++) rows.add(installedApps.get(i).label);
                        if (installedApps.size() > limit) rows.add("Ver mais apps em breve");
                    }
                    break;
                case 2:
                    Collections.addAll(rows, "Chrome", "Pesquisar", "YouTube Web", "Favoritos em breve");
                    break;
                case 3:
                    Collections.addAll(rows, "Abrir arquivos", "Downloads", "Pendrive / USB", "Galeria em breve");
                    break;
                case 4:
                    Collections.addAll(rows, "Prévia do Ambiente", "Foto local em breve", "Pasta de fotos em breve", "Vídeo local em breve", "Link do YouTube em breve");
                    break;
                default:
                    Collections.addAll(rows, "Configurações Android", "Permitir sobreposição", "Wi‑Fi", "Apps", "Definir Opening OS como Home");
                    break;
            }
            return rows;
        }

        private int getRowCountForTab() {
            return getRowsForTab().size();
        }

        private String getPreviewTitle() {
            List<String> rows = getRowsForTab();
            String row = rows.isEmpty() ? "" : rows.get(Math.min(selectedRow, rows.size() - 1));
            if (selectedTab == 4) return "Ambiente / Quadro Vivo";
            if (selectedTab == 1) return "Apps do projetor";
            return row;
        }

        private String getPreviewSubtitle() {
            switch (selectedTab) {
                case 0:
                    return "Base de central para projetor: TV, HDMI, streaming, modo cinema e futura camada flutuante.";
                case 1:
                    return "Lista os aplicativos instalados e abre direto pelo controle remoto. Favoritos e organização avançada entram nas próximas versões.";
                case 2:
                    return "Área de internet para navegador, links rápidos e YouTube web. A integração de links salvos será adicionada depois.";
                case 3:
                    return "Área preparada para abrir arquivos locais, downloads, fotos, vídeos e mídia USB quando o firmware permitir.";
                case 4:
                    return "Quando ninguém mexer, o projetor poderá mostrar fotos de família, paisagens, vídeos locais ou links configurados.";
                default:
                    return "Configurações rápidas do Android e preparação para permissões de overlay/floating layer.";
            }
        }

        private String getHeroLine() {
            switch (selectedTab) {
                case 0: return "Home estilo IPTV para substituir a tela padrão";
                case 1: return installedApps.size() + " apps detectados";
                case 2: return "Internet e links rápidos";
                case 3: return "Mídia local e arquivos";
                case 4: return "Tela viva para fotos, vídeos e paisagens";
                default: return "Controle do Opening OS";
            }
        }

        private boolean shouldShowAmbient() {
            long idleMs = System.currentTimeMillis() - lastInputAt;
            return ambientPreview || idleMs > 1000L * 60L * 3L;
        }

        private void activateSelected() {
            if (selectedTab == 0) {
                if (selectedRow == 0) openHdmiHint();
                else if (selectedRow == 1) launchPackageCandidates("com.google.android.youtube.tv", "com.google.android.youtube");
                else if (selectedRow == 2) launchByLabelContains("unitv");
                else if (selectedRow == 3) toast("Modo Cinema será refinado nas próximas versões.");
                else if (selectedRow == 4) openOverlaySettings();
            } else if (selectedTab == 1) {
                if (selectedRow < installedApps.size()) launchIntent(installedApps.get(selectedRow).launchIntent);
            } else if (selectedTab == 2) {
                if (selectedRow == 0) launchPackageCandidates("com.android.chrome", "org.chromium.chrome");
                else if (selectedRow == 1) openWeb("https://www.google.com/search?q=");
                else if (selectedRow == 2) openWeb("https://www.youtube.com/");
                else toast("Favoritos entram nas próximas versões.");
            } else if (selectedTab == 3) {
                openFilePicker();
            } else if (selectedTab == 4) {
                ambientPreview = true;
                invalidate();
            } else {
                if (selectedRow == 0) openSettings(Settings.ACTION_SETTINGS);
                else if (selectedRow == 1) openOverlaySettings();
                else if (selectedRow == 2) openSettings(Settings.ACTION_WIFI_SETTINGS);
                else if (selectedRow == 3) openSettings(Settings.ACTION_APPLICATION_SETTINGS);
                else toast("Para usar sempre: ao apertar HOME, escolha Opening OS e marque Sempre.");
            }
        }

        private void launchPackageCandidates(String... packages) {
            PackageManager pm = activity.getPackageManager();
            for (String pkg : packages) {
                Intent intent = pm.getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    launchIntent(intent);
                    return;
                }
            }
            toast("App não encontrado neste projetor.");
        }

        private void launchByLabelContains(String part) {
            String lower = part.toLowerCase(Locale.ROOT);
            for (LauncherApp app : installedApps) {
                if (app.label.toLowerCase(Locale.ROOT).contains(lower)
                        || app.packageName.toLowerCase(Locale.ROOT).contains(lower)) {
                    launchIntent(app.launchIntent);
                    return;
                }
            }
            toast("Aplicativo não encontrado. Abra pela aba APPS.");
        }

        private void launchIntent(Intent intent) {
            try {
                activity.startActivity(intent);
            } catch (Exception e) {
                toast("Não foi possível abrir este item.");
            }
        }

        private void openWeb(String url) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
            } catch (ActivityNotFoundException e) {
                toast("Nenhum navegador encontrado.");
            }
        }

        private void openFilePicker() {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                activity.startActivityForResult(intent, 200);
            } catch (Exception e) {
                openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
            }
        }

        private void openSettings(String action) {
            try {
                activity.startActivity(new Intent(action));
            } catch (Exception e) {
                toast("Configuração não disponível neste firmware.");
            }
        }

        private void openOverlaySettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception e) {
                openSettings(Settings.ACTION_SETTINGS);
            }
        }

        private void openHdmiHint() {
            toast("HDMI depende do firmware. Vamos mapear o atalho real depois do primeiro teste.");
            openSettings(Settings.ACTION_SETTINGS);
        }

        private void toast(String message) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                lastInputAt = System.currentTimeMillis();
                ambientPreview = false;
                invalidate();
                return true;
            }
            return true;
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
