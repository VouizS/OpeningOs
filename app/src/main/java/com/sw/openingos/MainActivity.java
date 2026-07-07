package com.sw.openingos;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextPaint;
import android.util.DisplayMetrics;
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
        enableImmersiveMode();
        openingView = new OpeningView(this);
        setContentView(openingView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enableImmersiveMode();
        if (openingView != null) openingView.refreshApps();
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
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
        private static final int BG = Color.rgb(0, 0, 0);
        private static final int PANEL = Color.rgb(13, 13, 15);
        private static final int PANEL_2 = Color.rgb(20, 20, 23);
        private static final int STROKE = Color.rgb(68, 68, 72);
        private static final int TEXT_MAIN = Color.WHITE;
        private static final int TEXT_MUTED = Color.rgb(205, 205, 210);
        private static final int TEXT_DIM = Color.rgb(145, 145, 150);
        private static final int ACCENT = Color.rgb(246, 198, 83);
        private static final int ACCENT_DARK = Color.rgb(118, 84, 25);

        private final Activity activity;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final SharedPreferences prefs;

        private final String[] tabs = {"TV", "APPS", "INTERNET", "ARQUIVOS", "CLIMA", "JOGOS", "AMBIENTE", "CONFIG"};
        private int selectedTab = 0;
        private int selectedRow = 0;
        private long lastInputAt = System.currentTimeMillis();
        private boolean ambientPreview = false;

        private float scale = 1f;
        private int screenW = 0;
        private int screenH = 0;
        private int densityDpi = 0;
        private float density = 1f;

        private final ArrayList<LauncherApp> installedApps = new ArrayList<>();
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEE, dd/MM", Locale.getDefault());

        OpeningView(Activity activity) {
            super(activity);
            this.activity = activity;
            this.prefs = activity.getSharedPreferences("opening_os", Context.MODE_PRIVATE);
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
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
                    if (ambientPreview) {
                        ambientPreview = false;
                        invalidate();
                        return true;
                    }
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
            updateMetrics(getWidth(), getHeight());

            drawBackground(canvas, screenW, screenH);

            if (shouldShowAmbient()) {
                drawAmbient(canvas, screenW, screenH);
                postInvalidateDelayed(1000);
                return;
            }

            drawTopBar(canvas, screenW, screenH);
            drawTabs(canvas, screenW, screenH);
            drawSideList(canvas, screenW, screenH);
            drawPreview(canvas, screenW, screenH);
            drawBottomHints(canvas, screenW, screenH);

            postInvalidateDelayed(1000);
        }

        private void updateMetrics(int w, int h) {
            screenW = Math.max(w, 1);
            screenH = Math.max(h, 1);

            DisplayMetrics dm = getResources().getDisplayMetrics();
            densityDpi = dm.densityDpi;
            density = dm.density;

            float bySize = Math.min(screenW / 1280f, screenH / 720f);
            scale = clamp(bySize, 0.68f, 1.10f);

            // Alguns projetores Android usam DPI estranho. A UI não usa dp bruto,
            // mas registra o DPI para diagnóstico e pequenos ajustes.
            if (densityDpi >= 300 && screenH <= 720) {
                scale *= 0.90f;
            } else if (densityDpi <= 160 && screenH >= 720) {
                scale *= 1.03f;
            }
            scale = clamp(scale, 0.62f, 1.12f);
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            canvas.drawColor(BG);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(5, 5, 6));
            rect.set(u(18), u(96), w - u(18), h - u(56));
            canvas.drawRoundRect(rect, u(18), u(18), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(1.4f)));
            paint.setColor(Color.rgb(32, 32, 36));
            canvas.drawRoundRect(rect, u(18), u(18), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawTopBar(Canvas canvas, int w, int h) {
            float pad = u(30);

            text.setTextAlign(Paint.Align.LEFT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(20));
            text.setColor(TEXT_MAIN);
            canvas.drawText("Opening OS", pad, u(38), text);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(10));
            text.setColor(TEXT_DIM);
            canvas.drawText("v0.1.1 • UI adaptativa para projetor", pad, u(57), text);

            String time = timeFormat.format(new Date());
            String date = dateFormat.format(new Date());
            text.setTextAlign(Paint.Align.RIGHT);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(22));
            text.setColor(TEXT_MAIN);
            canvas.drawText(time, w - pad, u(37), text);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(10));
            text.setColor(TEXT_DIM);
            canvas.drawText(date, w - pad, u(56), text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawTabs(Canvas canvas, int w, int h) {
            float pad = u(30);
            float y = u(84);
            float gap = u(7);
            float available = w - pad * 2 - gap * (tabs.length - 1);
            float itemW = available / tabs.length;
            float itemH = u(34);

            for (int i = 0; i < tabs.length; i++) {
                boolean active = i == selectedTab;
                float x = pad + i * (itemW + gap);
                rect.set(x, y, x + itemW, y + itemH);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(active ? ACCENT : Color.rgb(23, 23, 26));
                canvas.drawRoundRect(rect, u(12), u(12), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, u(1.1f)));
                paint.setColor(active ? Color.rgb(255, 225, 139) : Color.rgb(48, 48, 52));
                canvas.drawRoundRect(rect, u(12), u(12), paint);
                paint.setStyle(Paint.Style.FILL);

                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextSize(t(10.5f));
                text.setColor(active ? Color.rgb(0, 0, 0) : TEXT_MUTED);
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(tabs[i], rect.centerX(), rect.centerY() + u(4), text);
            }
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawSideList(Canvas canvas, int w, int h) {
            float pad = u(30);
            float top = u(136);
            float bottom = h - u(78);
            float sideW = clamp(w * 0.32f, u(280), u(370));
            float rowH = u(43);

            rect.set(pad, top, pad + sideW, bottom);
            paint.setColor(PANEL);
            canvas.drawRoundRect(rect, u(16), u(16), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(1.1f)));
            paint.setColor(STROKE);
            canvas.drawRoundRect(rect, u(16), u(16), paint);
            paint.setStyle(Paint.Style.FILL);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(13));
            text.setColor(TEXT_MAIN);
            canvas.drawText(getSideTitle(), pad + u(18), top + u(30), text);

            List<String> rows = getRowsForTab();
            int visibleRows = Math.max(4, (int) ((bottom - top - u(55)) / rowH));
            int total = rows.size();
            int offset = 0;
            if (total > visibleRows) {
                offset = selectedRow - visibleRows / 2;
                offset = Math.max(0, Math.min(offset, total - visibleRows));
            }
            int end = Math.min(total, offset + visibleRows);

            for (int i = offset; i < end; i++) {
                float ry = top + u(44) + (i - offset) * rowH;
                boolean active = i == selectedRow;
                rect.set(pad + u(12), ry, pad + sideW - u(12), ry + rowH - u(6));

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(active ? ACCENT : PANEL_2);
                canvas.drawRoundRect(rect, u(10), u(10), paint);

                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, u(0.8f)));
                paint.setColor(active ? Color.rgb(255, 229, 150) : Color.rgb(42, 42, 46));
                canvas.drawRoundRect(rect, u(10), u(10), paint);
                paint.setStyle(Paint.Style.FILL);

                text.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                text.setTextSize(t(12));
                text.setColor(active ? Color.BLACK : TEXT_MUTED);
                String label = ellipsize(rows.get(i), sideW - u(54));
                canvas.drawText(label, rect.left + u(14), rect.centerY() + u(4), text);
            }

            if (total > visibleRows) {
                text.setTypeface(Typeface.DEFAULT);
                text.setTextSize(t(9.5f));
                text.setColor(TEXT_DIM);
                String count = (selectedRow + 1) + "/" + total;
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(count, pad + sideW - u(16), bottom - u(14), text);
                text.setTextAlign(Paint.Align.LEFT);
            }
        }

        private void drawPreview(Canvas canvas, int w, int h) {
            float pad = u(30);
            float top = u(136);
            float bottom = h - u(78);
            float sideW = clamp(w * 0.32f, u(280), u(370));
            float x = pad + sideW + u(18);
            float y = top;
            float width = w - x - pad;
            float height = bottom - top;

            rect.set(x, y, x + width, y + height);
            paint.setColor(PANEL);
            canvas.drawRoundRect(rect, u(16), u(16), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(1.2f)));
            paint.setColor(STROKE);
            canvas.drawRoundRect(rect, u(16), u(16), paint);
            paint.setStyle(Paint.Style.FILL);

            String title = getPreviewTitle();
            String subtitle = getPreviewSubtitle();

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(24));
            text.setColor(TEXT_MAIN);
            drawMultiline(canvas, title, x + u(28), y + u(45), width - u(56), u(29), 2);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(12.5f));
            text.setColor(TEXT_MUTED);
            drawMultiline(canvas, subtitle, x + u(28), y + u(95), width - u(56), u(18), 3);

            float heroTop = y + u(155);
            float heroHeight = Math.max(u(145), height - u(215));
            drawHeroPanel(canvas, x + u(28), heroTop, width - u(56), heroHeight);
        }

        private void drawHeroPanel(Canvas canvas, float x, float y, float width, float height) {
            rect.set(x, y, x + width, y + height);
            paint.setColor(Color.rgb(10, 10, 12));
            canvas.drawRoundRect(rect, u(14), u(14), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(1.1f)));
            paint.setColor(ACCENT_DARK);
            canvas.drawRoundRect(rect, u(14), u(14), paint);
            paint.setStyle(Paint.Style.FILL);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(18));
            text.setColor(ACCENT);
            drawMultiline(canvas, getHeroLine(), x + u(22), y + u(37), width - u(44), u(22), 2);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(11.5f));
            text.setColor(TEXT_MUTED);
            drawMultiline(canvas, getHeroDetails(), x + u(22), y + u(82), width - u(44), u(17), 4);

            float tileY = y + height - u(64);
            float gap = u(12);
            float tileW = (width - gap * 2 - u(44)) / 3f;
            drawSmallTile(canvas, x + u(22), tileY, tileW, "OK", "Abrir");
            drawSmallTile(canvas, x + u(22) + tileW + gap, tileY, tileW, "D-PAD", "Navegar");
            drawSmallTile(canvas, x + u(22) + (tileW + gap) * 2, tileY, tileW, "DPI", densityDpi + " dpi");
        }

        private void drawSmallTile(Canvas canvas, float x, float y, float w, String title, String sub) {
            rect.set(x, y, x + w, y + u(48));
            paint.setColor(Color.rgb(25, 25, 28));
            canvas.drawRoundRect(rect, u(10), u(10), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(0.8f)));
            paint.setColor(Color.rgb(55, 55, 60));
            canvas.drawRoundRect(rect, u(10), u(10), paint);
            paint.setStyle(Paint.Style.FILL);

            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(10.5f));
            text.setColor(TEXT_MAIN);
            canvas.drawText(ellipsize(title, w - u(18)), x + u(10), y + u(20), text);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(9));
            text.setColor(TEXT_DIM);
            canvas.drawText(ellipsize(sub, w - u(18)), x + u(10), y + u(37), text);
        }

        private void drawBottomHints(Canvas canvas, int w, int h) {
            float y = h - u(32);
            float pad = u(30);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(10.5f));
            text.setColor(TEXT_DIM);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("OK abrir • ← → categorias • ↑ ↓ itens • apps detectados: " + installedApps.size(), pad, y, text);

            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Tela " + screenW + "x" + screenH + " • dpi " + densityDpi + " • escala " + String.format(Locale.US, "%.2f", scale), w - pad, y, text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private void drawAmbient(Canvas canvas, int w, int h) {
            canvas.drawColor(BG);

            rect.set(u(45), u(45), w - u(45), h - u(45));
            paint.setColor(Color.rgb(8, 8, 10));
            canvas.drawRoundRect(rect, u(22), u(22), paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, u(1.2f)));
            paint.setColor(ACCENT_DARK);
            canvas.drawRoundRect(rect, u(22), u(22), paint);
            paint.setStyle(Paint.Style.FILL);

            text.setTextAlign(Paint.Align.CENTER);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(t(48));
            text.setColor(TEXT_MAIN);
            canvas.drawText(timeFormat.format(new Date()), w / 2f, h / 2f - u(10), text);

            text.setTypeface(Typeface.DEFAULT);
            text.setTextSize(t(15));
            text.setColor(TEXT_MUTED);
            canvas.drawText("Modo Ambiente • foto, pasta, vídeo e YouTube serão configuráveis nas próximas versões", w / 2f, h / 2f + u(35), text);
            text.setTextSize(t(10.5f));
            text.setColor(TEXT_DIM);
            canvas.drawText("Pressione qualquer botão para voltar", w / 2f, h - u(70), text);
            text.setTextAlign(Paint.Align.LEFT);
        }

        private String getSideTitle() {
            switch (selectedTab) {
                case 0: return "Central de TV";
                case 1: return "Todos os aplicativos";
                case 2: return "Internet";
                case 3: return "Arquivos";
                case 4: return "Clima";
                case 5: return "Jogos ao vivo";
                case 6: return "Modo Ambiente";
                default: return "Configurações";
            }
        }

        private List<String> getRowsForTab() {
            ArrayList<String> rows = new ArrayList<>();
            switch (selectedTab) {
                case 0:
                    Collections.addAll(rows, "HDMI / Entrada", "YouTube", "UniTV / IPTV", "Modo Cinema", "Floating Layer");
                    break;
                case 1:
                    if (installedApps.isEmpty()) {
                        rows.add("Nenhum app encontrado");
                    } else {
                        for (LauncherApp app : installedApps) rows.add(app.label);
                    }
                    break;
                case 2:
                    Collections.addAll(rows, "Chrome", "Busca Google", "YouTube Web", "Favoritos em breve");
                    break;
                case 3:
                    Collections.addAll(rows, "Abrir arquivos", "Armazenamento interno", "Galeria/Fotos em breve", "USB/Pendrive depende do firmware");
                    break;
                case 4:
                    Collections.addAll(rows, "Clima: não configurado", "Adicionar cidade em breve", "Abrir clima na web");
                    break;
                case 5:
                    Collections.addAll(rows, "Jogos de hoje: não configurado", "Abrir jogos de hoje na web", "Tabela/placar em breve", "Meus times em breve");
                    break;
                case 6:
                    Collections.addAll(rows, "Prévia do Ambiente", "Foto local em breve", "Pasta de fotos em breve", "Vídeo local em breve", "Link do YouTube em breve");
                    break;
                default:
                    Collections.addAll(rows, "Configurações Android", "Configurar app Home", "Permitir sobreposição", "Wi‑Fi", "Apps Android", "Info tela/DPI");
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
            if (selectedTab == 1) return "Todos os apps instalados";
            if (selectedTab == 6) return "Ambiente / Quadro Vivo";
            return row;
        }

        private String getPreviewSubtitle() {
            switch (selectedTab) {
                case 0:
                    return "Central para projetor com HDMI, streaming, modo cinema e futura camada flutuante.";
                case 1:
                    return "Lista todos os aplicativos instalados juntos. Nas próximas versões entram filtros, favoritos e organização manual.";
                case 2:
                    return "Área para navegador, pesquisa, YouTube Web e links rápidos configuráveis.";
                case 3:
                    return "Área para arquivos locais, downloads, fotos, vídeos e mídia USB quando o firmware permitir.";
                case 4:
                    return "A seção de clima existe sem inventar dados. A configuração real de cidade/provedor entra em versão futura.";
                case 5:
                    return "A seção de jogos ao vivo existe sem dados falsos. Por enquanto pode abrir busca web; depois entra placar/tabela real.";
                case 6:
                    return "Quando ninguém mexer, o projetor poderá mostrar fotos de família, paisagens, vídeos locais ou links configurados.";
                default:
                    return "Configurações rápidas do Android, app Home, overlay e informações reais de tela/DPI.";
            }
        }

        private String getHeroLine() {
            switch (selectedTab) {
                case 0: return "Home escura de alto contraste para projeção";
                case 1: return installedApps.size() + " apps detectados no projetor";
                case 2: return "Internet e links rápidos";
                case 3: return "Mídia local e arquivos";
                case 4: return "Clima real será configurado por cidade/provedor";
                case 5: return "Jogos de hoje e tabela ao vivo sem dados falsos";
                case 6: return "Tela viva para fotos, vídeos e paisagens";
                default: return "Resolução, DPI e escala lidos do projetor";
            }
        }

        private String getHeroDetails() {
            if (selectedTab == 7) {
                return "Tela: " + screenW + "x" + screenH + " • densityDpi: " + densityDpi + " • density: "
                        + String.format(Locale.US, "%.2f", density) + " • escala UI: " + String.format(Locale.US, "%.2f", scale)
                        + ". Se a opção de Home não aparecer, o firmware pode estar travando o launcher padrão.";
            }
            if (selectedTab == 1) {
                return "Use ↑ ↓ para passar por todos os apps. A lista agora não fica limitada aos primeiros aplicativos.";
            }
            if (selectedTab == 4) {
                return "Não há clima automático ainda. O app só mostra dados quando configurarmos uma fonte real.";
            }
            if (selectedTab == 5) {
                return "Não há placar falso. O botão web abre jogos de hoje no navegador enquanto criamos integração real.";
            }
            return "Interface redesenhada com escala baseada na resolução da tela, sem depender de dp grande do firmware.";
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
                if (selectedRow == 0) openFilePicker();
                else if (selectedRow == 1) openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                else toast("Função preparada, mas ainda não conectada a uma fonte real.");
            } else if (selectedTab == 4) {
                if (selectedRow == 2) openWeb("https://www.google.com/search?q=clima+hoje");
                else toast("Clima real ainda não configurado. Sem dados falsos.");
            } else if (selectedTab == 5) {
                if (selectedRow == 1) openWeb("https://www.google.com/search?q=jogos+de+hoje+futebol");
                else toast("Placar/tabela real ainda não configurado. Sem dados falsos.");
            } else if (selectedTab == 6) {
                ambientPreview = true;
                invalidate();
            } else {
                if (selectedRow == 0) openSettings(Settings.ACTION_SETTINGS);
                else if (selectedRow == 1) openHomeSettings();
                else if (selectedRow == 2) openOverlaySettings();
                else if (selectedRow == 3) openSettings(Settings.ACTION_WIFI_SETTINGS);
                else if (selectedRow == 4) openSettings(Settings.ACTION_APPLICATION_SETTINGS);
                else toast("Tela " + screenW + "x" + screenH + " • dpi " + densityDpi + " • escala " + String.format(Locale.US, "%.2f", scale));
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

        private void openHomeSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
                activity.startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                activity.startActivity(intent);
            } catch (Exception e) {
                toast("Se o menu Home não aparecer, o firmware pode bloquear launcher padrão.");
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
            toast("HDMI depende do firmware. Vamos mapear o atalho real depois dos testes.");
            openSettings(Settings.ACTION_SETTINGS);
        }

        private void toast(String message) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            lastInputAt = System.currentTimeMillis();
            ambientPreview = false;
            invalidate();
            return true;
        }

        private float u(float value) {
            return value * scale;
        }

        private float t(float value) {
            return value * scale;
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private String ellipsize(String value, float maxWidth) {
            if (value == null) return "";
            if (text.measureText(value) <= maxWidth) return value;
            String suffix = "…";
            int end = value.length();
            while (end > 1 && text.measureText(value.substring(0, end) + suffix) > maxWidth) {
                end--;
            }
            return value.substring(0, Math.max(1, end)) + suffix;
        }

        private void drawMultiline(Canvas canvas, String value, float x, float y, float maxWidth, float lineHeight, int maxLines) {
            if (value == null || value.length() == 0 || maxLines <= 0) return;
            String[] words = value.split(" ");
            StringBuilder line = new StringBuilder();
            float currentY = y;
            int lines = 0;

            for (String word : words) {
                String next = line.length() == 0 ? word : line + " " + word;
                if (text.measureText(next) > maxWidth && line.length() > 0) {
                    lines++;
                    if (lines >= maxLines) {
                        canvas.drawText(ellipsize(line.toString(), maxWidth), x, currentY, text);
                        return;
                    }
                    canvas.drawText(line.toString(), x, currentY, text);
                    currentY += lineHeight;
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }

            if (line.length() > 0) {
                lines++;
                if (lines > maxLines) {
                    canvas.drawText(ellipsize(line.toString(), maxWidth), x, currentY, text);
                } else {
                    canvas.drawText(line.toString(), x, currentY, text);
                }
            }
        }
    }
}
