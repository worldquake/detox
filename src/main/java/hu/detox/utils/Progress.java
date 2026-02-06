package hu.detox.utils;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Stack;

public class Progress {
    private static final Logger logger = LogManager.getLogger(Progress.class);
    public static final Progress INSTANCE;

    static {
        INSTANCE = new Progress();
    }

    private transient int percent;
    private long total;
    private long processed;
    private final Stack<String> what = new Stack<String>();
    private final transient Timer timer = new Timer(10000, new ActionListener() {
        @Override
        public void actionPerformed(final ActionEvent e) {
            if (Progress.this.gui != null) {
                Progress.this.gui.setEnabled(false);
            }
        }
    });

    private transient JProgressBar gui;

    public Progress() {
        this.reset();
    }

    public synchronized Progress add(final long num) {
        if (num != 0) {
            this.setTotal(this.total + num);
        }
        return this;
    }

    public synchronized void end() {
        this.end(null);
    }

    public synchronized void end(final String add) {
        this.processed = this.total;
        String tt = "Done";
        if (!this.what.isEmpty()) {
            final String end = this.what.pop();
            tt += ": " + end;
            if (this.gui == null) {
                System.err.println();
            } else {
                this.timer.start();
            }
        }
        this.showProgress(tt + (StringUtils.isBlank(add) ? "" : " -> " + add));
    }

    public synchronized void endNext() {
        this.endNext(null);
    }

    public synchronized void endNext(final int step) {
        this.endNext();
        this.step(step);
    }

    public synchronized void endNext(final String add) {
        String wht = this.what.pop();
        wht = "Finished: " + wht + (StringUtils.isBlank(add) ? "" : " -> " + add);
        if (this.gui == null) {
            System.err.print(wht + " ");
        } else {
            this.gui.setEnabled(true);
            this.showProgress(wht);
        }
    }

    public void flash() {
        this.flash(null);
    }

    public synchronized void flash(final String major) {
        this.showMajor(major);
        if (this.gui == null) {
            System.err.print(" ");
        } else {
            this.gui.setBackground(Color.RED);
        }
    }

    public int getPercent() {
        return this.percent;
    }

    public long getProcessed() {
        return this.processed;
    }

    public long getTotal() {
        return this.total;
    }

    public boolean hasGui() {
        return this.gui != null;
    }

    public synchronized void nextIs(final String wht) {
        this.nextIs(wht, 0);
    }

    public synchronized void nextIs(final String wht, final long add) {
        this.add(add);
        this.what.push(wht);
        if (this.gui == null) {
            System.err.print(wht + "[" + this.total + "] ");
        } else {
            this.gui.setEnabled(true);
            this.showProgress(wht);
        }
    }

    public synchronized Progress reset() {
        this.percent = 0;
        this.processed = 0;
        this.total = 0;
        this.what.clear();
        return this.showProgress(null);
    }

    public void setPercent(final int percent) {
        this.percent = percent;
    }

    public void setProcessed(final long processed) {
        this.processed = processed;
    }

    public void setTotal(final long total) {
        this.total = total;
        if (this.gui != null) {
            this.gui.setMinimum(0);
            this.gui.setMaximum(100);
            this.showProgress(null);
        }
    }

    public void setWhat(final String wht) {
        this.what.set(0, wht);
    }

    private void showMajor(final String major) {
        if (StringUtils.isBlank(major)) {
            return;
        }
        if (this.gui == null) {
            System.err.print(major);
            Progress.logger.info(major);
        }
    }

    private Progress showProgress(final String major) {
        final int per;
        if (this.total == 0) {
            per = 0;
        } else {
            per = Math.round((float) this.processed / this.total * 100);
        }
        this.showMajor(major);
        if (this.gui == null) {
            if (this.percent == per) {
                if (StringUtils.isNotBlank(major)) {
                    System.err.print(", ");
                }
            } else {
                System.err.print((major != null ? " " : "") + per + "%, ");
            }
        } else {
            this.timer.stop();
            this.gui.setBackground(null);
            final String txt = (CollectionUtils.isEmpty(this.what) ? //
                    "Awaiting for execution, last completed at " + new Date() //
                    : StringUtils.join(this.what, " => ")).trim();
            this.gui.setString(
                    txt + " >> " + NumberFormat.getInstance().format(this.processed) + "/" + NumberFormat.getInstance().format(this.total) + " : " + per + "%");
            this.gui.setValue(per);
        }
        this.percent = per;
        return this;
    }

    public synchronized void start(final String wht) {
        this.start(wht, 0);
    }

    public synchronized void start(final String wht, final long siz) {
        this.reset();
        this.nextIs(wht, siz);
    }

    public void step() {
        this.step(null);
    }

    public void step(final long num) {
        this.step(num, null);
    }

    public synchronized void step(final long num, final String major) {
        if (Thread.interrupted()) {
            throw new IllegalStateException("Thread Interrupted");
        }
        this.processed += num;
        if (this.processed > this.total) {
            this.processed = this.total;
        }
        if (num == 0) {
            this.flash(major);
        } else {
            this.showProgress(major);
        }
    }

    public void step(final String major) {
        this.step(1, major);
    }
}
