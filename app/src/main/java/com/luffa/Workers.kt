package com.luffa

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class AutoSmsWorker(ctx: Context, params: WorkerParameters) 
    : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // SMS পাঠানোর code এখানে লিখবে
        return Result.success()
    }
}

class AutoLocationWorker(ctx: Context, params: WorkerParameters) 
    : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Location পাঠানোর code এখানে লিখবে
        return Result.success()
    }
}
