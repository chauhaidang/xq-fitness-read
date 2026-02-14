import type { Request, Response } from 'express';
import * as reportService from '../services/reportService';

export async function getWeeklyReport(req: Request, res: Response): Promise<void> {
  const routineId = parseInt(String(req.params.routineId), 10);
  if (Number.isNaN(routineId)) {
    res.status(400).json({ code: 'BAD_REQUEST', message: 'Invalid routineId', timestamp: new Date().toISOString() });
    return;
  }
  const weekStartDate = req.query.weekStartDate as string | undefined;
  try {
    const data = await reportService.getWeeklyReport(routineId, weekStartDate);
    res.json(data);
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    if (message.includes('Routine not found')) {
      res.status(404).json({ code: 'NOT_FOUND', message, timestamp: new Date().toISOString() });
      return;
    }
    res.status(500).json({
      code: 'INTERNAL_ERROR',
      message,
      timestamp: new Date().toISOString(),
    });
  }
}
