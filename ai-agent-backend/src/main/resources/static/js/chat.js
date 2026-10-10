/**
 * Aura AI - Classic & Unique Chat Client
 * Uses pure vanilla JavaScript to interact seamlessly with /chat API
 */

(function () {
    'use strict';

    // State & Storage Keys
    const STORAGE_KEY_SESSIONS = 'aura_chat_sessions_v1';
    const STORAGE_KEY_ACTIVE = 'aura_chat_active_session_id';
    const STORAGE_KEY_SOUND = 'aura_chat_sound_enabled';
    const STORAGE_KEY_THEME = 'aura_chat_theme';

    let sessions = [];
    let activeSessionId = null;
    let isGenerating = false;
    let soundEnabled = localStorage.getItem(STORAGE_KEY_SOUND) !== 'false'; // default true
    let isSpeaking = false;

    // DOM Elements
    const chatContainer = document.getElementById('chatMessagesContainer');
    const emptyStateHero = document.getElementById('emptyStateHero');
    const messageInput = document.getElementById('messageInput');
    const sendBtn = document.getElementById('sendBtn');
    const typingIndicator = document.getElementById('typingIndicator');
    const sessionListContainer = document.getElementById('sessionListContainer');
    const btnNewChat = document.getElementById('btnNewChat');
    const btnClearChat = document.getElementById('btnClearChat');
    const btnExportChat = document.getElementById('btnExportChat');
    const btnToggleSound = document.getElementById('btnToggleSound');
    const soundIconOn = document.getElementById('soundIconOn');
    const soundIconOff = document.getElementById('soundIconOff');
    const btnToggleTheme = document.getElementById('btnToggleTheme');
    const themeIconDark = document.getElementById('themeIconDark');
    const themeIconLight = document.getElementById('themeIconLight');
    const charCounter = document.getElementById('charCounter');
    const sidebarDrawer = document.getElementById('sidebarDrawer');
    const btnToggleSidebar = document.getElementById('btnToggleSidebar');
    const btnCloseSidebarMobile = document.getElementById('btnCloseSidebarMobile');
    const sidebarBackdrop = document.getElementById('sidebarBackdrop');

    // =========================================================================
    // Web Audio Synthesizer (Zero-dependency tactile sound effects)
    // =========================================================================
    let audioCtx = null;
    function getAudioContext() {
        if (!audioCtx) {
            const AudioContextClass = window.AudioContext || window.webkitAudioContext;
            if (AudioContextClass) {
                audioCtx = new AudioContextClass();
            }
        }
        if (audioCtx && audioCtx.state === 'suspended') {
            audioCtx.resume();
        }
        return audioCtx;
    }

    function playSendSound() {
        if (!soundEnabled) return;
        try {
            const ctx = getAudioContext();
            if (!ctx) return;
            const osc = ctx.createOscillator();
            const gain = ctx.createGain();
            osc.type = 'sine';
            osc.frequency.setValueAtTime(520, ctx.currentTime);
            osc.frequency.exponentialRampToValueAtTime(780, ctx.currentTime + 0.08);
            gain.gain.setValueAtTime(0.06, ctx.currentTime);
            gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.12);
            osc.connect(gain);
            gain.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.13);
        } catch (e) {
            // Audio policy or unsupported
        }
    }

    function playReceiveSound() {
        if (!soundEnabled) return;
        try {
            const ctx = getAudioContext();
            if (!ctx) return;
            // Harmonic chime chord
            [660, 880, 1320].forEach((freq, idx) => {
                const osc = ctx.createOscillator();
                const gain = ctx.createGain();
                osc.type = 'triangle';
                osc.frequency.setValueAtTime(freq, ctx.currentTime + idx * 0.04);
                gain.gain.setValueAtTime(0.045, ctx.currentTime + idx * 0.04);
                gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.35 + idx * 0.04);
                osc.connect(gain);
                gain.connect(ctx.destination);
                osc.start(ctx.currentTime + idx * 0.04);
                osc.stop(ctx.currentTime + 0.4 + idx * 0.04);
            });
        } catch (e) {
            // Audio policy or unsupported
        }
    }

    // =========================================================================
    // Session & Persistence Management
    // =========================================================================
    function loadSessionsFromStorage() {
        try {
            const raw = localStorage.getItem(STORAGE_KEY_SESSIONS);
            sessions = raw ? JSON.parse(raw) : [];
        } catch (e) {
            sessions = [];
        }

        activeSessionId = localStorage.getItem(STORAGE_KEY_ACTIVE);
        if (!sessions.some(s => s.id === activeSessionId)) {
            if (sessions.length > 0) {
                activeSessionId = sessions[0].id;
            } else {
                createNewSession(false);
            }
        }
    }

    function saveSessionsToStorage() {
        try {
            localStorage.setItem(STORAGE_KEY_SESSIONS, JSON.stringify(sessions));
            if (activeSessionId) {
                localStorage.setItem(STORAGE_KEY_ACTIVE, activeSessionId);
            }
        } catch (e) {
            console.warn('Could not save sessions to localStorage', e);
        }
    }

    function getActiveSession() {
        return sessions.find(s => s.id === activeSessionId);
    }

    function createNewSession(switchImmediately = true) {
        const newSession = {
            id: 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 6),
            title: 'New Conversation',
            createdAt: new Date().toISOString(),
            messages: []
        };
        sessions.unshift(newSession);
        if (switchImmediately) {
            activeSessionId = newSession.id;
        }
        saveSessionsToStorage();
        renderSessionList();
        renderActiveChat();
        return newSession;
    }

    function deleteSession(sessionId, event) {
        if (event) event.stopPropagation();
        sessions = sessions.filter(s => s.id !== sessionId);
        if (activeSessionId === sessionId) {
            if (sessions.length > 0) {
                activeSessionId = sessions[0].id;
            } else {
                createNewSession(false);
            }
        }
        saveSessionsToStorage();
        renderSessionList();
        renderActiveChat();
    }

    function selectSession(sessionId) {
        activeSessionId = sessionId;
        saveSessionsToStorage();
        renderSessionList();
        renderActiveChat();
        closeSidebarMobile();
    }

    function renderSessionList() {
        if (!sessionListContainer) return;
        sessionListContainer.innerHTML = '';

        if (sessions.length === 0) {
            sessionListContainer.innerHTML = `
                <div class="px-3 py-6 text-center text-xs text-stone-400">
                    No conversations yet.<br>Start chatting below.
                </div>
            `;
            return;
        }

        sessions.forEach(session => {
            const isActive = session.id === activeSessionId;
            const item = document.createElement('div');
            item.className = `group relative flex items-center justify-between px-3 py-2.5 rounded-xl cursor-pointer text-sm transition-all duration-150 ${
                isActive
                    ? 'bg-amber-500/15 border border-amber-500/30 text-amber-300 font-medium'
                    : 'text-stone-300 hover:bg-stone-800/60 hover:text-stone-100 border border-transparent'
            }`;
            
            const messageCount = session.messages ? session.messages.length : 0;
            const titleText = escapeHtml(session.title || 'Conversation');

            item.innerHTML = `
                <div class="flex items-center space-x-2.5 min-w-0 flex-1 pr-2">
                    <svg class="w-4 h-4 flex-shrink-0 ${isActive ? 'text-amber-400' : 'text-stone-400'}" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                    </svg>
                    <span class="truncate text-xs tracking-wide">${titleText}</span>
                </div>
                <div class="flex items-center space-x-1">
                    <span class="text-[10px] text-stone-400 px-1.5 py-0.5 rounded-full ${isActive ? 'bg-amber-500/20 text-amber-200' : 'bg-stone-800 text-stone-400'}">${messageCount}</span>
                    <button class="btn-del-session opacity-0 group-hover:opacity-100 hover:text-red-400 p-1 rounded transition-opacity" title="Delete conversation">
                        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                    </button>
                </div>
            `;

            item.addEventListener('click', () => selectSession(session.id));
            const delBtn = item.querySelector('.btn-del-session');
            delBtn.addEventListener('click', (e) => deleteSession(session.id, e));

            sessionListContainer.appendChild(item);
        });
    }

    // =========================================================================
    // Chat Rendering
    // =========================================================================
    function renderActiveChat() {
        const session = getActiveSession();
        if (!session || !session.messages || session.messages.length === 0) {
            chatContainer.innerHTML = '';
            if (emptyStateHero) emptyStateHero.classList.remove('hidden');
            return;
        }

        if (emptyStateHero) emptyStateHero.classList.add('hidden');
        chatContainer.innerHTML = '';

        session.messages.forEach(msg => {
            appendMessageElement(msg, false);
        });

        scrollToBottom();
    }

    function appendMessageElement(msg, animate = true) {
        if (emptyStateHero) emptyStateHero.classList.add('hidden');

        const isUser = msg.role === 'user';
        const msgWrapper = document.createElement('div');
        msgWrapper.className = `flex w-full mb-6 ${isUser ? 'justify-end' : 'justify-start'} ${animate ? 'animate-message' : ''}`;

        const timestampStr = msg.timestamp ? formatTime(msg.timestamp) : formatTime(new Date());

        if (isUser) {
            msgWrapper.innerHTML = `
                <div class="flex items-start max-w-[85%] md:max-w-[70%] space-x-3 flex-row-reverse space-x-reverse">
                    <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-amber-500 to-amber-700 flex items-center justify-center text-stone-900 font-bold text-xs shadow-md shadow-amber-900/20 flex-shrink-0">
                        YOU
                    </div>
                    <div class="flex flex-col items-end">
                        <div class="px-5 py-3.5 rounded-2xl rounded-tr-sm bg-gradient-to-br from-stone-800 to-stone-900 border border-stone-700/60 text-stone-100 shadow-md text-sm leading-relaxed whitespace-pre-wrap break-words">
                            ${escapeHtml(msg.content)}
                        </div>
                        <span class="text-[11px] text-stone-400 mt-1 mr-1">${timestampStr}</span>
                    </div>
                </div>
            `;
        } else {
            const formattedContent = parseMarkdown(msg.content);
            const msgId = 'msg_' + Math.random().toString(36).substr(2, 9);

            msgWrapper.innerHTML = `
                <div class="flex items-start max-w-[92%] md:max-w-[80%] space-x-3.5">
                    <div class="w-9 h-9 rounded-xl bg-gradient-to-br from-amber-400 via-amber-500 to-yellow-600 flex items-center justify-center text-stone-950 font-bold text-xs shadow-lg shadow-amber-500/20 flex-shrink-0 aura-jewel">
                        <svg class="w-5 h-5 text-stone-950" fill="currentColor" viewBox="0 0 24 24">
                            <path d="M12 2L14.4 8.6L21 11L14.4 13.4L12 20L9.6 13.4L3 11L9.6 8.6L12 2Z" />
                        </svg>
                    </div>
                    <div class="flex flex-col min-w-0 flex-1">
                        <div class="relative px-5 py-4 rounded-2xl rounded-tl-sm glass-panel border border-amber-500/20 text-stone-200 shadow-xl">
                            <div class="markdown-content" id="${msgId}">
                                ${formattedContent}
                            </div>
                            <!-- Action Bar -->
                            <div class="flex items-center justify-between border-t border-stone-700/40 mt-3 pt-2 text-[11px] text-stone-400">
                                <span class="flex items-center space-x-1.5 text-stone-400">
                                    <span class="w-1.5 h-1.5 rounded-full bg-emerald-400"></span>
                                    <span>Gemini 3.8 Flash</span>
                                </span>
                                <div class="flex items-center space-x-2">
                                    <button class="btn-read-aloud flex items-center space-x-1 hover:text-amber-300 transition-colors p-1 rounded" title="Read aloud">
                                        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                                        </svg>
                                    </button>
                                    <button class="btn-copy-msg flex items-center space-x-1 hover:text-amber-300 transition-colors p-1 rounded" title="Copy message">
                                        <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
                                        </svg>
                                        <span class="copy-label">Copy</span>
                                    </button>
                                    <span>•</span>
                                    <span>${timestampStr}</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            `;

            // Attach action listeners
            const copyBtn = msgWrapper.querySelector('.btn-copy-msg');
            if (copyBtn) {
                copyBtn.addEventListener('click', () => {
                    copyToClipboard(msg.content, copyBtn.querySelector('.copy-label'));
                });
            }

            const readBtn = msgWrapper.querySelector('.btn-read-aloud');
            if (readBtn) {
                readBtn.addEventListener('click', () => {
                    toggleReadAloud(msg.content, readBtn);
                });
            }
        }

        chatContainer.appendChild(msgWrapper);
    }

    function scrollToBottom() {
        if (!chatContainer) return;
        setTimeout(() => {
            chatContainer.scrollTo({
                top: chatContainer.scrollHeight,
                behavior: 'smooth'
            });
        }, 60);
    }

    // =========================================================================
    // Markdown Parser & Helpers
    // =========================================================================
    function parseMarkdown(text) {
        if (!text) return '';
        if (window.marked && typeof window.marked.parse === 'function') {
            try {
                return window.marked.parse(text);
            } catch (e) {
                console.warn('Marked parse error', e);
            }
        }
        // Fallback simple formatter
        return escapeHtml(text).replace(/\n/g, '<br>');
    }

    function escapeHtml(str) {
        if (!str) return '';
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatTime(dateVal) {
        const d = new Date(dateVal);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    function copyToClipboard(text, labelEl) {
        navigator.clipboard.writeText(text).then(() => {
            if (labelEl) {
                const old = labelEl.textContent;
                labelEl.textContent = 'Copied!';
                labelEl.classList.add('text-amber-400');
                setTimeout(() => {
                    labelEl.textContent = old;
                    labelEl.classList.remove('text-amber-400');
                }, 1800);
            }
        }).catch(err => {
            console.error('Failed to copy', err);
        });
    }

    function toggleReadAloud(text, btnEl) {
        if (!('speechSynthesis' in window)) {
            alert('Speech synthesis is not supported in this browser.');
            return;
        }

        if (isSpeaking) {
            window.speechSynthesis.cancel();
            isSpeaking = false;
            btnEl.classList.remove('text-amber-400');
            return;
        }

        // Clean markdown symbols for cleaner speech
        const cleanText = text
            .replace(/[*#`_\[\]()]/g, '')
            .replace(/\n+/g, ' ');

        const utterance = new SpeechSynthesisUtterance(cleanText);
        utterance.rate = 1.0;
        utterance.pitch = 1.0;

        utterance.onstart = () => {
            isSpeaking = true;
            btnEl.classList.add('text-amber-400');
        };

        utterance.onend = utterance.onerror = () => {
            isSpeaking = false;
            btnEl.classList.remove('text-amber-400');
        };

        window.speechSynthesis.speak(utterance);
    }

    // =========================================================================
    // API Interaction: Send Message to /chat
    // =========================================================================
    async function sendMessage(text) {
        if (!text || !text.trim() || isGenerating) return;

        const query = text.trim();
        let session = getActiveSession();
        if (!session) {
            session = createNewSession(true);
        }

        // Auto title conversation after first query
        if (session.messages.length === 0) {
            session.title = query.length > 30 ? query.substring(0, 30) + '...' : query;
        }

        // Append User Message
        const userMsg = {
            role: 'user',
            content: query,
            timestamp: new Date().toISOString()
        };
        session.messages.push(userMsg);
        appendMessageElement(userMsg, true);
        saveSessionsToStorage();
        renderSessionList();
        scrollToBottom();

        // Clear input field and play send sound
        if (messageInput) {
            messageInput.value = '';
            adjustTextareaHeight();
            updateCharCount();
        }
        playSendSound();

        // Show typing indicator
        setGeneratingState(true);

        try {
            // First attempt: POST /chat with JSON
            const res = await fetch('/chat', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json, text/plain, */*'
                },
                body: JSON.stringify({ query: query })
            });

            if (!res.ok) {
                // If 405/415 or error, try fallback POST with text/plain
                const fallbackRes = await fetch('/chat', {
                    method: 'POST',
                    headers: { 'Content-Type': 'text/plain' },
                    body: query
                });

                if (!fallbackRes.ok) {
                    // Try GET /chat?query=...
                    const getRes = await fetch('/chat?query=' + encodeURIComponent(query));
                    if (!getRes.ok) {
                        const errText = await getRes.text();
                        throw new Error(`Server returned error ${getRes.status}: ${errText || 'Request failed'}`);
                    }
                    const textReply = await getRes.text();
                    handleAssistantResponse(session, textReply);
                    return;
                }
                const textReply = await fallbackRes.text();
                handleAssistantResponse(session, textReply);
                return;
            }

            const contentType = res.headers.get('content-type') || '';
            let assistantReply = '';

            if (contentType.includes('application/json')) {
                const data = await res.json();
                assistantReply = data.response || data.reply || data.content || JSON.stringify(data);
            } else {
                assistantReply = await res.text();
            }

            handleAssistantResponse(session, assistantReply);

        } catch (err) {
            console.error('Chat error:', err);
            const errorMsg = {
                role: 'assistant',
                content: `⚠️ **Connection Notice:** Unable to reach the agent service at \`/chat\`.\n\n*Error details:* ${escapeHtml(err.message || 'Unknown network error')}\n\n*Please ensure the Spring Boot server is running and your GEMINI_API_KEY is configured.*`,
                timestamp: new Date().toISOString()
            };
            session.messages.push(errorMsg);
            appendMessageElement(errorMsg, true);
            saveSessionsToStorage();
            scrollToBottom();
        } finally {
            setGeneratingState(false);
        }
    }

    function handleAssistantResponse(session, replyText) {
        const finalContent = replyText || 'I received your request, but no response was returned.';
        const assistantMsg = {
            role: 'assistant',
            content: finalContent,
            timestamp: new Date().toISOString()
        };
        session.messages.push(assistantMsg);
        saveSessionsToStorage();
        renderSessionList();
        appendMessageElement(assistantMsg, true);
        playReceiveSound();
        scrollToBottom();
    }

    function setGeneratingState(generating) {
        isGenerating = generating;
        if (typingIndicator) {
            if (generating) {
                typingIndicator.classList.remove('hidden');
                typingIndicator.classList.add('flex');
                scrollToBottom();
            } else {
                typingIndicator.classList.add('hidden');
                typingIndicator.classList.remove('flex');
            }
        }
        if (sendBtn) {
            sendBtn.disabled = generating;
            if (generating) {
                sendBtn.classList.add('opacity-50', 'cursor-not-allowed');
            } else {
                sendBtn.classList.remove('opacity-50', 'cursor-not-allowed');
            }
        }
    }

    // =========================================================================
    // Input Handling & Textarea Auto-expand
    // =========================================================================
    function adjustTextareaHeight() {
        if (!messageInput) return;
        messageInput.style.height = 'auto';
        const scrollHeight = messageInput.scrollHeight;
        const maxHeight = 160; // Max ~6 lines
        messageInput.style.height = Math.min(scrollHeight, maxHeight) + 'px';
    }

    function updateCharCount() {
        if (!charCounter || !messageInput) return;
        const len = messageInput.value.length;
        charCounter.textContent = `${len} / 2000`;
    }

    // =========================================================================
    // Export Chat Session
    // =========================================================================
    function exportActiveChat() {
        const session = getActiveSession();
        if (!session || !session.messages || session.messages.length === 0) {
            alert('No messages in the current conversation to export.');
            return;
        }

        let mdContent = `# ${session.title || 'Aura AI Conversation'}\n`;
        mdContent += `*Date: ${new Date(session.createdAt || Date.now()).toLocaleString()}*\n\n---\n\n`;

        session.messages.forEach(m => {
            const speaker = m.role === 'user' ? '### 👤 You' : '### ✨ Aura Assistant';
            const time = m.timestamp ? ` *(${new Date(m.timestamp).toLocaleTimeString()})*` : '';
            mdContent += `${speaker}${time}\n\n${m.content}\n\n---\n\n`;
        });

        const blob = new Blob([mdContent], { type: 'text/markdown;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        const filename = (session.title || 'conversation')
            .toLowerCase()
            .replace(/[^a-z0-9]/g, '_')
            .substring(0, 25);
        a.href = url;
        a.download = `Aura_Chat_${filename}_${Date.now()}.md`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    // =========================================================================
    // Theme Management
    // =========================================================================
    function initTheme() {
        const savedTheme = localStorage.getItem(STORAGE_KEY_THEME) || 'dark';
        applyTheme(savedTheme);
    }

    function applyTheme(theme) {
        const html = document.documentElement;
        if (theme === 'light') {
            html.classList.add('light');
            if (themeIconDark) themeIconDark.classList.add('hidden');
            if (themeIconLight) themeIconLight.classList.remove('hidden');
        } else {
            html.classList.remove('light');
            if (themeIconDark) themeIconDark.classList.remove('hidden');
            if (themeIconLight) themeIconLight.classList.add('hidden');
        }
        localStorage.setItem(STORAGE_KEY_THEME, theme);
    }

    function toggleTheme() {
        const isLight = document.documentElement.classList.contains('light');
        applyTheme(isLight ? 'dark' : 'light');
    }

    // =========================================================================
    // Sound Toggle
    // =========================================================================
    function updateSoundUI() {
        if (!soundIconOn || !soundIconOff) return;
        if (soundEnabled) {
            soundIconOn.classList.remove('hidden');
            soundIconOff.classList.add('hidden');
        } else {
            soundIconOn.classList.add('hidden');
            soundIconOff.classList.remove('hidden');
        }
    }

    function toggleSound() {
        soundEnabled = !soundEnabled;
        localStorage.setItem(STORAGE_KEY_SOUND, soundEnabled ? 'true' : 'false');
        updateSoundUI();
        if (soundEnabled) {
            playSendSound();
        }
    }

    // =========================================================================
    // Mobile Drawer Handlers
    // =========================================================================
    function openSidebarMobile() {
        if (!sidebarDrawer) return;
        sidebarDrawer.classList.remove('-translate-x-full');
        if (sidebarBackdrop) sidebarBackdrop.classList.remove('hidden');
    }

    function closeSidebarMobile() {
        if (!sidebarDrawer) return;
        sidebarDrawer.classList.add('-translate-x-full');
        if (sidebarBackdrop) sidebarBackdrop.classList.add('hidden');
    }

    // =========================================================================
    // Initializer & Event Listeners
    // =========================================================================
    function init() {
        loadSessionsFromStorage();
        initTheme();
        updateSoundUI();

        // Send Button Click
        if (sendBtn) {
            sendBtn.addEventListener('click', () => {
                if (messageInput) sendMessage(messageInput.value);
            });
        }

        // Textarea Keydown (Enter to send, Shift+Enter for newline)
        if (messageInput) {
            messageInput.addEventListener('keydown', (e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    sendMessage(messageInput.value);
                }
            });

            messageInput.addEventListener('input', () => {
                adjustTextareaHeight();
                updateCharCount();
            });
        }

        // New Chat Button
        if (btnNewChat) {
            btnNewChat.addEventListener('click', () => {
                createNewSession(true);
                closeSidebarMobile();
                if (messageInput) messageInput.focus();
            });
        }

        // Clear Current Chat Button
        if (btnClearChat) {
            btnClearChat.addEventListener('click', () => {
                const session = getActiveSession();
                if (session) {
                    if (confirm('Clear messages in this conversation?')) {
                        session.messages = [];
                        saveSessionsToStorage();
                        renderSessionList();
                        renderActiveChat();
                    }
                }
            });
        }

        // Export Chat Button
        if (btnExportChat) {
            btnExportChat.addEventListener('click', exportActiveChat);
        }

        // Sound Toggle
        if (btnToggleSound) {
            btnToggleSound.addEventListener('click', toggleSound);
        }

        // Theme Toggle
        if (btnToggleTheme) {
            btnToggleTheme.addEventListener('click', toggleTheme);
        }

        // Sidebar Mobile Toggles
        if (btnToggleSidebar) {
            btnToggleSidebar.addEventListener('click', openSidebarMobile);
        }
        if (btnCloseSidebarMobile) {
            btnCloseSidebarMobile.addEventListener('click', closeSidebarMobile);
        }
        if (sidebarBackdrop) {
            sidebarBackdrop.addEventListener('click', closeSidebarMobile);
        }

        // Prompt Chips & Cards (Event delegation)
        document.addEventListener('click', (e) => {
            const promptTarget = e.target.closest('[data-prompt]');
            if (promptTarget) {
                const promptText = promptTarget.getAttribute('data-prompt');
                if (promptText) {
                    sendMessage(promptText);
                }
            }
        });

        // Focus input on load
        if (messageInput) {
            messageInput.focus();
        }
    }

    // Run when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
