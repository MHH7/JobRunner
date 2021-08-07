package ir.sharif.math.ap.hw3;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JobRunner {
    final Boss boss;
    HashMap<String, Integer> src;
    int free;
    ArrayList<Job> jobs;
    final Locker locker;
    ThreadPool threadPool;

    public JobRunner(Map<String, Integer> resources, List<Job> jobs, int initialThreadNumber) {
        locker = new Locker();
        boss = new Boss();
        src = new HashMap<>();
        free = 0;
        this.jobs = new ArrayList<>();
        for(String s : resources.keySet()){
            src.put(s,resources.get(s));
        }
        for(Job j : jobs)this.jobs.add(j);
        threadPool = new ThreadPool(initialThreadNumber);
        setThreadNumbers(initialThreadNumber);
        boss.start();
    }

    public void setThreadNumbers(int threadNumbers) {
        locker.lock1();
        int x = threadPool.threadNumbers;
        threadPool.setThreadNumbers(threadNumbers);
        if(x < threadNumbers){
            synchronized (boss) {
                boss.notify();
            }
        }
        locker.release();
    }

    private class Boss extends Thread{
        @Override
        public void run() {
            while (true){
                locker.lock3();
                for(int i = 0;i < jobs.size();i++){
                    Job j = jobs.get(i);
                    int p = 0;
                    for(String s : j.getResources()){
                        if(src.get(s) == 0)p = 1;
                    }
                    if(p == 0 && free > 0){
                        jobs.remove(i);
                        i--;
                        free--;
                        Work work = new Work(j);
                        threadPool.invokeLater(work);
                    }
                }
                locker.release();
                if(jobs.size() == 0)break;
                synchronized (boss) {
                    try {
                        boss.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }

    private class Work implements Runnable{
        Job task;

        public Work(Job task){
            this.task = task;
            for(String s : task.getResources()){
                src.put(s,src.get(s) - 1);
            }
        }

        @Override
        public void run() {
            long time = task.getRunnable().run();
            locker.lock2();
            free++;
            try {
                Thread.sleep(time);
            } catch (InterruptedException e) {
            }
            for(String s : task.getResources()){
                src.put(s,src.get(s) + 1);
            }
            synchronized (boss) {
                boss.notify();
            }
            locker.release();
        }
    }

    private class Locker {
        private final Object object;
        private volatile boolean azad;
        private volatile int cnt1 = 0;
        private volatile int cnt2 = 0;

        public Locker() {
            azad = true;
            object = new Object();
        }

        void lock1() {
            synchronized (object) {
                cnt1++;
                while (!azad) {
                    try {
                        object.wait();
                    } catch (InterruptedException e) {
                    }
                }
                cnt1--;
                azad = false;
            }
        }

        void lock2() {
            synchronized (object) {
                cnt2++;
                while (!azad || cnt1 > 0) {
                    try {
                        object.wait();
                    } catch (InterruptedException e) {
                    }
                }
                cnt2--;
                azad = false;
            }
        }

        void lock3() {
            synchronized (object) {
                while (!azad || cnt1 > 0 || cnt2 > 0) {
                    try {
                        object.wait();
                    } catch (InterruptedException e) {
                    }
                }
                azad = false;
            }
        }

        void release() {
            synchronized (object) {
                azad = true;
                object.notifyAll();
            }
        }
    }

    private class ThreadPool {
        private int threadNumbers;
        final ArrayList<Runnable> tasks;
        ArrayList<Worker> workers;
        int maxID = 0;

        public ThreadPool(int threadNumbers) {
            workers = new ArrayList<>();
            tasks = new ArrayList<>();
            this.threadNumbers = threadNumbers;
            addThread(threadNumbers);
        }

        public int getThreadNumbers() {
            return threadNumbers;
        }

        public void setThreadNumbers(int threadNumbers) {
            synchronized (tasks) {
                if (this.threadNumbers < threadNumbers) {
                    addThread(threadNumbers - this.threadNumbers);
                } else {
                    removeThread(this.threadNumbers - threadNumbers);
                }
                this.threadNumbers = threadNumbers;
            }
        }

        public void addThread(int x){
            synchronized (tasks) {
                for (int i = 0; i < x; i++) {
                    maxID++;
                    Worker worker = new Worker();
                    worker.id = maxID;
                    workers.add(worker);
                    free++;
                    worker.start();
                }
            }
        }

        public void removeThread(int x){
            synchronized (tasks) {
                for (int i = 0; i < x; i++) {
                    workers.get(0).delete();
                    workers.remove(0);
                }
            }
        }

        public void invokeLater(Runnable runnable) {
            synchronized (tasks) {
                tasks.add(runnable);
                tasks.notifyAll();
            }
        }

        public class Worker extends Thread{
            boolean deleted = false;
            int id;

            @Override
            public void run() {
                Runnable task;

                while (true){

                    synchronized (tasks){
                        while (tasks.isEmpty()){
                            try {
                                tasks.wait();
                                if(deleted)break;
                            } catch (InterruptedException e) {
                            }
                        }
                        if(deleted)break;
                        task = tasks.get(0);
                        tasks.remove(0);
                    }
                    try {
                        task.run();
                    }catch (Throwable t){
                        synchronized (tasks) {
                            tasks.notifyAll();
                        }
                    }
                    synchronized (tasks) {
                        tasks.notifyAll();
                        if (deleted) break;
                    }
                }
            }

            public void delete(){
                deleted = true;
                synchronized (tasks) {
                    tasks.notifyAll();
                }
            }
        }
    }
}

