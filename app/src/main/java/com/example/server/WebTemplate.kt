package com.example.server

import com.example.data.model.FileCategory
import com.example.data.model.StreamFileItem
import com.example.util.NetworkUtils

object WebTemplate {

    fun renderHomePage(
        files: List<StreamFileItem>,
        serverIp: String,
        port: Int,
        totalBytesStreamed: Long,
        botUsername: String
    ): String {
        val totalSizeStr = NetworkUtils.formatBytes(files.sumOf { it.fileSize })
        val streamedStr = NetworkUtils.formatBytes(totalBytesStreamed)

        val fileRows = if (files.isEmpty()) {
            """
            <div class="empty-state">
                <div class="empty-icon">📁</div>
                <h3>No files streamed yet</h3>
                <p>Send files to your Telegram Bot <strong>@$botUsername</strong> or add them in the TeleStream Android App to generate instant stream links.</p>
            </div>
            """.trimIndent()
        } else {
            files.joinToString("\n") { file ->
                val icon = when (file.category) {
                    FileCategory.VIDEO -> "🎬"
                    FileCategory.AUDIO -> "🎵"
                    FileCategory.IMAGE -> "🖼️"
                    FileCategory.ARCHIVE -> "📦"
                    FileCategory.DOCUMENT -> "📄"
                    FileCategory.OTHER -> "📁"
                }
                val formattedSize = NetworkUtils.formatBytes(file.fileSize)
                val formattedDate = NetworkUtils.formatDate(file.createdAt)
                val downloadUrl = "/download/${file.id}"
                val playerUrl = "/player/${file.id}"

                """
                <div class="file-card">
                    <div class="file-icon-box">$icon</div>
                    <div class="file-info">
                        <div class="file-name" title="${file.fileName}">${escapeHtml(file.fileName)}</div>
                        <div class="file-meta">
                            <span>📊 $formattedSize</span>
                            <span>•</span>
                            <span>⏱️ $formattedDate</span>
                            <span>•</span>
                            <span>📥 ${file.downloadCount} downloads</span>
                        </div>
                    </div>
                    <div class="file-actions">
                        <a href="$playerUrl" class="btn btn-stream">🎬 Play / View</a>
                        <a href="$downloadUrl" class="btn btn-download" download>📥 Download</a>
                        <button class="btn btn-copy" onclick="copyLink(window.location.origin + '$downloadUrl')">📋 Link</button>
                    </div>
                </div>
                """.trimIndent()
            }
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>TeleStream • Telegram Direct File Streamer</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
            <style>
                :root {
                    --bg-color: #0b141f;
                    --card-bg: #132030;
                    --card-hover: #1a2d42;
                    --border-color: #20354e;
                    --primary: #0088cc;
                    --primary-light: #29b6f6;
                    --accent-cyan: #06b6d4;
                    --accent-green: #10b981;
                    --text-main: #f1f5f9;
                    --text-muted: #94a3b8;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    background-color: var(--bg-color);
                    color: var(--text-main);
                    line-height: 1.6;
                    min-height: 100vh;
                    padding-bottom: 40px;
                }
                header {
                    background: linear-gradient(135deg, #07111c 0%, #0e2034 100%);
                    border-bottom: 1px solid var(--border-color);
                    padding: 24px 20px;
                    position: sticky;
                    top: 0;
                    z-index: 100;
                    backdrop-filter: blur(12px);
                }
                .container { max-width: 1000px; margin: 0 auto; padding: 0 16px; }
                .nav-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    flex-wrap: wrap;
                    gap: 16px;
                }
                .brand {
                    display: flex;
                    align-items: center;
                    gap: 12px;
                    text-decoration: none;
                    color: var(--text-main);
                }
                .brand-logo {
                    width: 44px;
                    height: 44px;
                    background: linear-gradient(135deg, var(--primary) 0%, var(--accent-cyan) 100%);
                    border-radius: 12px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 22px;
                    box-shadow: 0 4px 16px rgba(0, 136, 204, 0.4);
                }
                .brand-text h1 { font-size: 20px; font-weight: 700; }
                .brand-text p { font-size: 12px; color: var(--accent-cyan); font-weight: 500; }
                .server-badge {
                    display: inline-flex;
                    align-items: center;
                    gap: 8px;
                    background: rgba(16, 185, 129, 0.15);
                    border: 1px solid rgba(16, 185, 129, 0.3);
                    color: #34d399;
                    padding: 6px 14px;
                    border-radius: 999px;
                    font-size: 13px;
                    font-weight: 600;
                }
                .pulse-dot {
                    width: 8px;
                    height: 8px;
                    background: #10b981;
                    border-radius: 50%;
                    box-shadow: 0 0 8px #10b981;
                    animation: pulse 2s infinite;
                }
                @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }
                
                .hero {
                    padding: 40px 0 24px;
                    text-align: center;
                }
                .hero h2 {
                    font-size: 32px;
                    font-weight: 800;
                    margin-bottom: 12px;
                    background: linear-gradient(90deg, #ffffff, #67e8f9);
                    -webkit-background-clip: text;
                    -webkit-text-fill-color: transparent;
                }
                .hero p {
                    color: var(--text-muted);
                    font-size: 16px;
                    max-width: 600px;
                    margin: 0 auto 28px;
                }
                .stats-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                    gap: 16px;
                    margin-bottom: 36px;
                }
                .stat-card {
                    background: var(--card-bg);
                    border: 1px solid var(--border-color);
                    border-radius: 16px;
                    padding: 20px;
                    text-align: center;
                }
                .stat-num { font-size: 24px; font-weight: 700; color: var(--primary-light); }
                .stat-label { font-size: 13px; color: var(--text-muted); margin-top: 4px; }
                
                .section-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    margin-bottom: 20px;
                }
                .section-title { font-size: 20px; font-weight: 700; }
                
                .file-list { display: flex; flex-direction: column; gap: 12px; }
                .file-card {
                    background: var(--card-bg);
                    border: 1px solid var(--border-color);
                    border-radius: 16px;
                    padding: 16px 20px;
                    display: flex;
                    align-items: center;
                    gap: 16px;
                    transition: all 0.2s ease;
                }
                .file-card:hover {
                    background: var(--card-hover);
                    border-color: #2f4b6d;
                    transform: translateY(-2px);
                }
                .file-icon-box {
                    width: 48px;
                    height: 48px;
                    background: rgba(0, 136, 204, 0.15);
                    border-radius: 12px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 24px;
                    flex-shrink: 0;
                }
                .file-info { flex: 1; min-width: 0; }
                .file-name {
                    font-size: 16px;
                    font-weight: 600;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    color: var(--text-main);
                }
                .file-meta {
                    display: flex;
                    gap: 8px;
                    font-size: 13px;
                    color: var(--text-muted);
                    margin-top: 4px;
                    flex-wrap: wrap;
                }
                .file-actions { display: flex; gap: 8px; flex-shrink: 0; }
                .btn {
                    padding: 8px 16px;
                    border-radius: 10px;
                    font-size: 14px;
                    font-weight: 600;
                    text-decoration: none;
                    cursor: pointer;
                    border: none;
                    transition: all 0.2s ease;
                    display: inline-flex;
                    align-items: center;
                    gap: 6px;
                }
                .btn-stream {
                    background: linear-gradient(135deg, var(--primary) 0%, #0284c7 100%);
                    color: white;
                }
                .btn-download {
                    background: #10b981;
                    color: white;
                }
                .btn-copy {
                    background: rgba(255, 255, 255, 0.08);
                    color: var(--text-main);
                    border: 1px solid var(--border-color);
                }
                .btn:hover { opacity: 0.9; transform: scale(1.02); }
                
                .empty-state {
                    background: var(--card-bg);
                    border: 2px dashed var(--border-color);
                    border-radius: 20px;
                    padding: 48px 24px;
                    text-align: center;
                }
                .empty-icon { font-size: 48px; margin-bottom: 12px; }
                .empty-state h3 { font-size: 20px; margin-bottom: 8px; }
                .empty-state p { color: var(--text-muted); font-size: 15px; max-width: 500px; margin: 0 auto; }
                
                .toast {
                    position: fixed;
                    bottom: 24px;
                    left: 50%;
                    transform: translateX(-50%) translateY(100px);
                    background: #10b981;
                    color: white;
                    padding: 12px 24px;
                    border-radius: 999px;
                    font-weight: 600;
                    box-shadow: 0 10px 25px rgba(0,0,0,0.5);
                    opacity: 0;
                    transition: all 0.3s ease;
                    z-index: 999;
                }
                .toast.show {
                    transform: translateX(-50%) translateY(0);
                    opacity: 1;
                }
                @media (max-width: 650px) {
                    .file-card { flex-direction: column; align-items: stretch; text-align: left; }
                    .file-actions { justify-content: flex-start; margin-top: 10px; width: 100%; }
                    .file-actions .btn { flex: 1; justify-content: center; }
                }
            </style>
        </head>
        <body>
            <header>
                <div class="container nav-row">
                    <a href="/" class="brand">
                        <div class="brand-logo">⚡</div>
                        <div class="brand-text">
                            <h1>TeleStream</h1>
                            <p>Direct File Streamer</p>
                        </div>
                    </a>
                    <div class="server-badge">
                        <span class="pulse-dot"></span>
                        <span>Server Online • $serverIp:$port</span>
                    </div>
                </div>
            </header>
            
            <main class="container">
                <section class="hero">
                    <h2>High-Speed Telegram Stream & Download</h2>
                    <p>Access files directly from Telegram without size limits, play videos online with full seeking support, or download at maximum browser speed.</p>
                    
                    <div class="stats-grid">
                        <div class="stat-card">
                            <div class="stat-num">${files.size}</div>
                            <div class="stat-label">Active Stream Files</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-num">$totalSizeStr</div>
                            <div class="stat-label">Total Media Size</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-num">$streamedStr</div>
                            <div class="stat-label">Total Streamed</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-num">@$botUsername</div>
                            <div class="stat-label">Telegram Bot</div>
                        </div>
                    </div>
                </section>
                
                <div class="section-header">
                    <div class="section-title">📂 Stream Files & Direct Links</div>
                </div>
                
                <div class="file-list">
                    $fileRows
                </div>
            </main>
            
            <div id="toast" class="toast">Link copied to clipboard!</div>
            
            <script>
                function copyLink(url) {
                    navigator.clipboard.writeText(url).then(() => {
                        const toast = document.getElementById('toast');
                        toast.classList.add('show');
                        setTimeout(() => toast.classList.remove('show'), 2500);
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    fun renderPlayerPage(
        file: StreamFileItem,
        serverIp: String,
        port: Int
    ): String {
        val streamUrl = "/stream/${file.id}"
        val downloadUrl = "/download/${file.id}"
        val formattedSize = NetworkUtils.formatBytes(file.fileSize)
        val formattedDate = NetworkUtils.formatDate(file.createdAt)

        val playerElement = when (file.category) {
            FileCategory.VIDEO -> {
                """
                <div class="media-container">
                    <video id="streamVideo" controls playsinline preload="metadata" poster="">
                        <source src="$streamUrl" type="${file.mimeType}">
                        Your browser does not support HTML5 video streaming.
                    </video>
                </div>
                """.trimIndent()
            }
            FileCategory.AUDIO -> {
                """
                <div class="audio-container">
                    <div class="audio-visual">
                        <div class="audio-disc">🎵</div>
                    </div>
                    <audio id="streamAudio" controls preload="metadata" style="width: 100%; margin-top: 20px;">
                        <source src="$streamUrl" type="${file.mimeType}">
                        Your browser does not support HTML5 audio playback.
                    </audio>
                </div>
                """.trimIndent()
            }
            FileCategory.IMAGE -> {
                """
                <div class="image-container">
                    <img src="$streamUrl" alt="${escapeHtml(file.fileName)}" style="max-width: 100%; max-height: 550px; border-radius: 12px; object-fit: contain;">
                </div>
                """.trimIndent()
            }
            else -> {
                """
                <div class="doc-container">
                    <div class="doc-icon">📄</div>
                    <h3>${escapeHtml(file.fileName)}</h3>
                    <p>Document / Binary File • $formattedSize</p>
                    <a href="$downloadUrl" class="btn btn-download" style="margin-top: 16px;" download>📥 Direct Download File</a>
                </div>
                """.trimIndent()
            }
        }

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>${escapeHtml(file.fileName)} • TeleStream Player</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap" rel="stylesheet">
            <style>
                :root {
                    --bg-color: #0b141f;
                    --card-bg: #132030;
                    --border-color: #20354e;
                    --primary: #0088cc;
                    --primary-light: #29b6f6;
                    --accent-cyan: #06b6d4;
                    --accent-green: #10b981;
                    --text-main: #f1f5f9;
                    --text-muted: #94a3b8;
                }
                * { box-sizing: border-box; margin: 0; padding: 0; }
                body {
                    font-family: 'Plus Jakarta Sans', sans-serif;
                    background-color: var(--bg-color);
                    color: var(--text-main);
                    line-height: 1.6;
                    min-height: 100vh;
                    padding-bottom: 40px;
                }
                header {
                    background: #07111c;
                    border-bottom: 1px solid var(--border-color);
                    padding: 18px 20px;
                }
                .container { max-width: 900px; margin: 0 auto; padding: 0 16px; }
                .nav-row { display: flex; align-items: center; justify-content: space-between; }
                .brand { display: flex; align-items: center; gap: 10px; text-decoration: none; color: var(--text-main); }
                .brand-logo { width: 36px; height: 36px; background: var(--primary); border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 18px; }
                .back-link { color: var(--accent-cyan); text-decoration: none; font-size: 14px; font-weight: 600; }
                
                .player-card {
                    background: var(--card-bg);
                    border: 1px solid var(--border-color);
                    border-radius: 20px;
                    margin-top: 30px;
                    overflow: hidden;
                    box-shadow: 0 12px 30px rgba(0,0,0,0.4);
                }
                .media-container {
                    background: #000000;
                    width: 100%;
                    max-height: 520px;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }
                video { width: 100%; max-height: 520px; outline: none; }
                
                .audio-container, .image-container, .doc-container {
                    padding: 40px 24px;
                    text-align: center;
                    background: linear-gradient(180deg, #0f1c2d 0%, #132030 100%);
                }
                .audio-visual { display: flex; justify-content: center; }
                .audio-disc {
                    width: 100px;
                    height: 100px;
                    background: linear-gradient(135deg, var(--primary), var(--accent-cyan));
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: 40px;
                    box-shadow: 0 8px 24px rgba(0, 136, 204, 0.4);
                    animation: spin 10s linear infinite;
                }
                @keyframes spin { 100% { transform: rotate(360deg); } }
                .doc-icon { font-size: 54px; margin-bottom: 12px; }
                
                .info-section { padding: 24px; }
                .file-title { font-size: 22px; font-weight: 700; word-break: break-word; }
                .file-meta-row {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 12px;
                    margin: 12px 0 24px;
                    color: var(--text-muted);
                    font-size: 14px;
                }
                .meta-badge {
                    background: rgba(255,255,255,0.06);
                    padding: 4px 12px;
                    border-radius: 8px;
                    border: 1px solid var(--border-color);
                }
                
                .action-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
                    gap: 12px;
                    margin-top: 20px;
                }
                .btn {
                    padding: 12px 20px;
                    border-radius: 12px;
                    font-size: 15px;
                    font-weight: 700;
                    text-decoration: none;
                    cursor: pointer;
                    border: none;
                    display: inline-flex;
                    align-items: center;
                    justify-content: center;
                    gap: 8px;
                    transition: all 0.2s ease;
                }
                .btn-download { background: #10b981; color: white; }
                .btn-stream { background: var(--primary); color: white; }
                .btn-copy { background: rgba(255, 255, 255, 0.1); color: var(--text-main); border: 1px solid var(--border-color); }
                .btn:hover { opacity: 0.9; transform: translateY(-2px); }
                
                .toast {
                    position: fixed;
                    bottom: 24px;
                    left: 50%;
                    transform: translateX(-50%) translateY(100px);
                    background: #10b981;
                    color: white;
                    padding: 12px 24px;
                    border-radius: 999px;
                    font-weight: 600;
                    opacity: 0;
                    transition: all 0.3s ease;
                    z-index: 999;
                }
                .toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }
            </style>
        </head>
        <body>
            <header>
                <div class="container nav-row">
                    <a href="/" class="brand">
                        <div class="brand-logo">⚡</div>
                        <span>TeleStream Web</span>
                    </a>
                    <a href="/" class="back-link">← Back to All Files</a>
                </div>
            </header>
            
            <main class="container">
                <div class="player-card">
                    $playerElement
                    
                    <div class="info-section">
                        <h1 class="file-title">${escapeHtml(file.fileName)}</h1>
                        
                        <div class="file-meta-row">
                            <span class="meta-badge">📦 $formattedSize</span>
                            <span class="meta-badge">🏷️ ${file.mimeType}</span>
                            <span class="meta-badge">📅 $formattedDate</span>
                            <span class="meta-badge">📥 ${file.downloadCount} Downloads</span>
                        </div>
                        
                        <div class="action-grid">
                            <a href="$downloadUrl" class="btn btn-download" download>📥 Direct Download</a>
                            <a href="$streamUrl" class="btn btn-stream" target="_blank">🔗 Raw Stream Link</a>
                            <button class="btn btn-copy" onclick="copyLink(window.location.origin + '$downloadUrl')">📋 Copy Direct Link</button>
                        </div>
                    </div>
                </div>
            </main>
            
            <div id="toast" class="toast">Direct Download Link copied!</div>
            
            <script>
                function copyLink(url) {
                    navigator.clipboard.writeText(url).then(() => {
                        const toast = document.getElementById('toast');
                        toast.classList.add('show');
                        setTimeout(() => toast.classList.remove('show'), 2500);
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
