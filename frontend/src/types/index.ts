export type UserRole = 'student' | 'teacher';

export type ActiveTab = 'today' | 'tomorrow' | 'week' | 'classrooms' | 'teachers' | 'changes' | 'profile' | 'admin';

export type ThemeId =
  | 'liquid-glass-dark'
  | 'liquid-glass-light';

export interface Lesson {
  lessonNumber: number;
  time: string;
  subject: string;
  subgroups?: { number: number; teacher: string; room: string }[];
  teacher?: string;
  room?: string;
  weekType?: string | null; // "red", "blue", or null/all
  groupName?: string;
  type: 'Пара' | 'Замена' | 'Отмена';
  changed?: boolean;
  changeText?: string;
  canceled?: boolean;
  originalSubject?: string;
  originalTeacher?: string;
  originalRoom?: string;
}

export interface DaySchedule {
  dayName: string;
  lessons: Lesson[];
  hasLessons: boolean;
}

export type ScheduleMap = Record<string, DaySchedule>;

export interface InfoResponse {
  weekBadge: string;
  isRedWeek: boolean;
  tomorrowRedWeek?: boolean;
  todayName: string;
  tomorrowName: string;
  changesDate: string;
  serverTime: string;
}

export interface GroupsResponse {
  courses: string[];
  groupsByCourse: Record<string, string[]>;
}

export interface TeachersResponse {
  letters: string[];
  teachers: string[];
}

export interface ChangeItem {
  slot: number;
  groupName: string;
  text: string;
  canceled: boolean;
}

export interface ChangesResponse {
  date: string;
  target: string;
  items: ChangeItem[];
}

export interface ClassroomOccupied {
  room: string;
  subject: string;
  teacher: string;
  groupName: string;
  time: string;
}

export interface ClassroomsResponse {
  day: string;
  slot: number;
  weekType: string;
  freeRooms: string[];
  occupiedRooms: ClassroomOccupied[];
  allRooms: string[];
}

export type MascotLookId = 'classic' | 'autumn' | 'winter' | 'spring' | 'summer' | 'graduate';

/** Огонёк и прогресс Парыча, считается на сервере по московскому времени. */
export interface MascotState {
  streak: number;
  best: number;
  freezes: number;
  maxFreezes: number;
  countedToday: boolean;
  studyDay: boolean;
  week: { label: string; state: 'done' | 'freeze' | 'missed' | 'today' | 'future' | 'off' }[];
  xp: number;
  level: number;
  levelName: string;
  levelXp: number;
  nextLevelXp: number;
  tasks: { id: string; title: string; hint: string | null; xp: number; done: boolean }[];
  look: MascotLookId;
  looks: { id: MascotLookId; name: string; level: number; unlocked: boolean }[];
  reminder: boolean;
  rewards: { type: 'streak' | 'level'; value: number; look: MascotLookId | null }[];
}
