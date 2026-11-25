# -*- coding: utf-8 -*-
"""Frontiers 图片转换工具（Windows 桌面端）
一键把 PNG/JPG 等图片转为 RGB、300 dpi、10 MB 以内的 LZW TIFF。
"""
import os
import sys
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox

import converter_core as core

APP_TITLE = 'Frontiers 图片转换工具（300 dpi / RGB / TIFF ≤ 10 MB）'
NOTICE = '本软件仅供学术投稿使用，禁止商用'


class App(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(APP_TITLE)
        self.geometry('860x620')
        self.minsize(760, 540)
        self._set_window_icon()
        self.files = []
        self.use_common_dir = tk.BooleanVar(value=False)
        self.common_dir = tk.StringVar(value='')
        self._build_ui()

    def _set_window_icon(self):
        base = getattr(sys, '_MEIPASS',
                       os.path.dirname(os.path.abspath(__file__)))
        ico = os.path.join(base, 'app.ico')
        if os.path.exists(ico):
            try:
                self.iconbitmap(default=ico)
            except tk.TclError:
                pass

    def _build_ui(self):
        pad = {'padx': 8, 'pady': 4}
        top = ttk.Frame(self)
        top.pack(fill='x', **pad)

        ttk.Button(top, text='选择图片', command=self.pick_files).pack(side='left', padx=4)
        ttk.Button(top, text='选择文件夹', command=self.pick_folder).pack(side='left', padx=4)
        ttk.Button(top, text='清空列表', command=self.clear).pack(side='left', padx=4)

        ttk.Checkbutton(top, text='统一输出到指定文件夹',
                        variable=self.use_common_dir,
                        command=self._toggle_common).pack(side='left', padx=14)
        self.dir_btn = ttk.Button(top, text='选择输出目录', command=self.pick_outdir, state='disabled')
        self.dir_btn.pack(side='left', padx=4)
        self.dir_label = ttk.Label(top, text='（默认：各图片旁 Frontiers_TIFF 文件夹）')
        self.dir_label.pack(side='left', padx=4)

        # file list
        mid = ttk.Frame(self)
        mid.pack(fill='both', expand=True, **pad)
        cols = ('file', 'status', 'info')
        self.tree = ttk.Treeview(mid, columns=cols, show='headings', height=10)
        self.tree.heading('file', text='文件')
        self.tree.heading('status', text='状态')
        self.tree.heading('info', text='输出信息')
        self.tree.column('file', width=420, anchor='w')
        self.tree.column('status', width=110, anchor='center')
        self.tree.column('info', width=280, anchor='w')
        vsb = ttk.Scrollbar(mid, orient='vertical', command=self.tree.yview)
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(side='left', fill='both', expand=True)
        vsb.pack(side='right', fill='y')

        bottom = ttk.Frame(self)
        bottom.pack(fill='x', **pad)
        self.progress = ttk.Progressbar(bottom, mode='determinate')
        self.progress.pack(side='left', fill='x', expand=True, padx=4)
        self.start_btn = ttk.Button(bottom, text='开始转换', command=self.start)
        self.start_btn.pack(side='left', padx=4)

        logf = ttk.LabelFrame(self, text='日志')
        logf.pack(fill='both', expand=False, **pad)
        self.log_text = tk.Text(logf, height=8, wrap='word')
        lsb = ttk.Scrollbar(logf, orient='vertical', command=self.log_text.yview)
        self.log_text.configure(yscrollcommand=lsb.set)
        self.log_text.pack(side='left', fill='both', expand=True)
        lsb.pack(side='right', fill='y')

        footer = ttk.Frame(self)
        footer.pack(fill='x', **pad)
        ttk.Label(footer, text=NOTICE, foreground='#b00020').pack(side='left', padx=4)

    # ---------- actions ----------
    def _toggle_common(self):
        if self.use_common_dir.get():
            self.dir_btn.configure(state='normal')
        else:
            self.dir_btn.configure(state='disabled')
            self.dir_label.configure(text='（默认：各图片旁 Frontiers_TIFF 文件夹）')

    def pick_outdir(self):
        d = filedialog.askdirectory(title='选择输出目录')
        if d:
            self.common_dir.set(d)
            self.dir_label.configure(text=d)

    def pick_files(self):
        types = [('图片文件', ' '.join('*' + e for e in core.INPUT_EXTS)), ('所有文件', '*.*')]
        picked = filedialog.askopenfilenames(title='选择图片', filetypes=types)
        self.add_files(picked)

    def pick_folder(self):
        d = filedialog.askdirectory(title='选择包含图片的文件夹')
        if not d:
            return
        picked = []
        for root, _, names in os.walk(d):
            for n in names:
                if n.lower().endswith(core.INPUT_EXTS):
                    picked.append(os.path.join(root, n))
        self.add_files(picked)

    def add_files(self, picked):
        existed = set(self.files)
        for f in picked:
            if f not in existed:
                self.files.append(f)
                self.tree.insert('', 'end', iid=f, values=(f, '待转换', ''))
        self.log('已添加 %d 个文件，当前共 %d 个' % (len(picked), len(self.files)))

    def clear(self):
        self.files = []
        for iid in self.tree.get_children():
            self.tree.delete(iid)
        self.progress.configure(value=0)

    def log(self, msg):
        self.log_text.insert('end', msg + '\n')
        self.log_text.see('end')

    def start(self):
        if not self.files:
            messagebox.showinfo('提示', '请先添加图片')
            return
        if self.use_common_dir.get() and not self.common_dir.get():
            messagebox.showinfo('提示', '请选择统一输出目录')
            return
        self.start_btn.configure(state='disabled')
        self.progress.configure(maximum=len(self.files), value=0)
        threading.Thread(target=self._worker, daemon=True).start()

    def _worker(self):
        ok = fail = 0
        for idx, src in enumerate(list(self.files), 1):
            name = os.path.basename(src)
            if self.use_common_dir.get():
                out_dir = self.common_dir.get()
            else:
                out_dir = os.path.join(os.path.dirname(src), 'Frontiers_TIFF')
            os.makedirs(out_dir, exist_ok=True)
            dst = core.output_path_for(src, out_dir)
            self.log('[%d/%d] %s' % (idx, len(self.files), name))
            try:
                w, h, size, scale = core.convert(src, dst, log=self.log)
                self.tree.item(src, values=(src, '完成',
                                            '%dx%d, %.2f MB' % (w, h, size / 1048576)))
                ok += 1
            except Exception as e:
                self.tree.item(src, values=(src, '失败', str(e)))
                self.log('  失败: %s' % e)
                fail += 1
            self.progress.configure(value=idx)
        self.log('全部结束：成功 %d，失败 %d' % (ok, fail))
        self.start_btn.configure(state='normal')
        messagebox.showinfo('完成', '转换结束\n成功 %d 个，失败 %d 个' % (ok, fail))


if __name__ == '__main__':
    App().mainloop()
