export interface DailyWord {
  date: string;
  word: string;
  hints: string[];
  fact: string;
}

export interface DailyWordResponse {
  date: string;
  word: string;
  hints: string[];
  fact: string;
}

export interface WordEntry {
  word: string;
  hints: string[];
  fact: string;
}
