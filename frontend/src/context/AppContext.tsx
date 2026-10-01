import React, { createContext, useContext, useEffect, useState, useCallback, useRef } from 'react';
import {
  ActiveTab,
  GroupsResponse,
  InfoResponse,
  ScheduleMap,
  TeachersResponse,
  UserRole,
} from '../types';
import { api, profileRequest, signedRequest } from '../api/client';
import { useTelegram } from '../hooks/useTelegram';

interface AppContextType {
  isAdmin: boolean;
  role: UserRole;
  setRole: (role: UserRole) => void;
  selectedGroup: string;
  setSelectedGroup: (group: string) => void;
  previewTeacher: string;
  setPreviewTeacher: (teacher: string) => void;
  selectedTeacher: string;
  setSelectedTeacher: (teacher: string) => void;
  activeTab: ActiveTab;
  setActiveTab: (tab: ActiveTab) => void;
  info: InfoResponse;
  groupsData: GroupsResponse;
  teachersData: TeachersResponse;
  schedule: ScheduleMap;
  loading: boolean;
  loadError: string;
  refreshing: boolean;
  previewWeekType: 'current' | 'red' | 'blue';
  setPreviewWeekType: (type: 'current' | 'red' | 'blue') => void;
  refreshAll: () => Promise<void>;
}

const AppContext = createContext<AppContextType | undefined>(undefined);

export const AppProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { haptic } = useTelegram();

  const [role, setRoleState] = useState<UserRole>(() => {
    return localStorage.getItem('konyaevo_role') === 'teacher' ? 'teacher' : 'student';
  });

  const [selectedGroup, setSelectedGroupState] = useState<string>(() => {
    return localStorage.getItem('konyaevo_role') === 'teacher' ? '' : localStorage.getItem('konyaevo_group') || '';
  });

  const [selectedTeacher, setSelectedTeacherState] = useState<string>(() => {
    return localStorage.getItem('konyaevo_role') === 'teacher' ? localStorage.getItem('konyaevo_teacher') || '' : '';
  });

  const [previewTeacher, setPreviewTeacher] = useState('');
  useEffect(() => { localStorage.removeItem(role === 'student' ? 'konyaevo_teacher' : 'konyaevo_group'); }, [role]);
  const [activeTab, setActiveTabState] = useState<ActiveTab>('today');
  const [info, setInfo] = useState<InfoResponse>({weekBadge: '', isRedWeek: false, todayName: '', tomorrowName: '', changesDate: '', serverTime: ''});
  const [groupsData, setGroupsData] = useState<GroupsResponse>({courses: [], groupsByCourse: {}});
  const [teachersData, setTeachersData] = useState<TeachersResponse>({letters: [], teachers: []});
  const [schedule, setSchedule] = useState<ScheduleMap>({});
  const requestVersion = useRef(0);
  const profileEdited = useRef(false);
  const [isAdmin, setIsAdmin] = useState(false);
  useEffect(() => {
    let active = true;
    api.isAdmin().then(allowed => { if (active) setIsAdmin(allowed); });
    return () => { active = false; };
  }, []);
  useEffect(() => {
    if (!window.Telegram?.WebApp?.initData) return;
    const heartbeat = setInterval(() => {
      if (!document.hidden) signedRequest('/profile/activity', {}).catch(() => { /* Ошибка статистики не влияет на расписание. */ });
    }, 300000);
    profileRequest().then(saved => {
      if (profileEdited.current) return;
      const restoredRole = saved.role === 'teacher' ? 'teacher' : 'student';
      const group = restoredRole === 'student' ? saved.group || '' : '';
      const teacher = restoredRole === 'teacher' ? saved.teacher || '' : '';
      if (restoredRole !== role || group !== selectedGroup || teacher !== selectedTeacher) {
        requestVersion.current++; setSchedule({});
      }
      setRoleState(restoredRole); setSelectedGroupState(group); setSelectedTeacherState(teacher);
      localStorage.setItem('konyaevo_role', restoredRole);
      localStorage.setItem('konyaevo_group', group); localStorage.setItem('konyaevo_teacher', teacher);
    }).catch(() => { /* Локальное расписание остаётся доступным при ошибке синхронизации. */ });
    return () => clearInterval(heartbeat);
  }, []);
  const [loadError, setLoadError] = useState('');
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [previewWeekType, setPreviewWeekType] = useState<'current' | 'red' | 'blue'>('current');

  const setRole = (newRole: UserRole) => {
    profileEdited.current = true;
    if (newRole === role) return;
    requestVersion.current++;
    setSchedule({}); setPreviewTeacher('');
    if (newRole === 'teacher') { setSelectedGroupState(''); localStorage.removeItem('konyaevo_group'); }
    else { setSelectedTeacherState(''); localStorage.removeItem('konyaevo_teacher'); }
    setRoleState(newRole);
    localStorage.setItem('konyaevo_role', newRole);
    haptic.selection();
  };

  const setSelectedGroup = (group: string) => {
    profileEdited.current = true;
    if (group === selectedGroup && role === 'student') return;
    requestVersion.current++; setSchedule({});
    if (group) {
      setRoleState('student'); localStorage.setItem('konyaevo_role', 'student');
      setSelectedTeacherState(''); localStorage.removeItem('konyaevo_teacher'); setPreviewTeacher('');
    }
    setSelectedGroupState(group);
    localStorage.setItem('konyaevo_group', group);
    haptic.selection();
  };

  const setSelectedTeacher = (teacher: string) => {
    profileEdited.current = true;
    if (teacher === selectedTeacher && role === 'teacher') return;
    requestVersion.current++; setSchedule({});
    if (teacher) {
      setRoleState('teacher'); localStorage.setItem('konyaevo_role', 'teacher');
      setSelectedGroupState(''); localStorage.removeItem('konyaevo_group'); setPreviewTeacher('');
    }
    setSelectedTeacherState(teacher);
    localStorage.setItem('konyaevo_teacher', teacher);
    haptic.selection();
  };

  const setActiveTab = (tab: ActiveTab) => {
    setActiveTabState(tab);
    haptic.impact('light');
  };

  const activeTarget = role === 'student' ? selectedGroup : selectedTeacher;
  const loadData = useCallback(async (isRefresh = false) => {
    const version = ++requestVersion.current;
    setLoadError('');
    if (isRefresh) setRefreshing(true);
    else setLoading(true);

    try {
      const [fetchedInfo, fetchedGroups, fetchedTeachers] = await Promise.all([
        api.getInfo(),
        api.getGroups(),
        api.getTeachers(),
      ]);

      if (version !== requestVersion.current) return;
      setInfo(fetchedInfo);
      setGroupsData(fetchedGroups);
      setTeachersData(fetchedTeachers);

      if (role === 'student' && selectedGroup && !Object.values(fetchedGroups.groupsByCourse).flat().includes(selectedGroup)) {
        setSelectedGroup('');
        setSchedule({});
        return false;
      }
      if (role === 'teacher' && selectedTeacher && !fetchedTeachers.teachers.includes(selectedTeacher)) {
        setSelectedTeacher('');
        setSchedule({});
        return false;
      }

      const activeGroup = selectedGroup;
      const activeTeacher = selectedTeacher;

      let fetchedSchedule: ScheduleMap;
      if (role === 'student' && activeGroup) {
        fetchedSchedule = await api.getGroupSchedule(activeGroup);
      } else if (role === 'teacher' && activeTeacher) {
        fetchedSchedule = await api.getTeacherSchedule(activeTeacher);
      } else {
        fetchedSchedule = {};
      }
      if (version === requestVersion.current) setSchedule(fetchedSchedule);
      return true;
    } catch (e) {
      if (version === requestVersion.current) {
        setSchedule({});
        setLoadError('Не удалось загрузить данные. Проверьте подключение и обновите расписание.');
      }
      return false;
    } finally {
      if (version === requestVersion.current) {
        setLoading(false);
        setRefreshing(false);
      }
    }
  }, [role, activeTarget]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const refreshAll = async () => {
    haptic.impact('medium');
    api.refreshCatalogs();
    const success = await loadData(true);
    haptic.notification(success ? 'success' : 'error');
  };

  return (
    <AppContext.Provider
      value={{
        role,
        setRole,
        selectedGroup,
        setSelectedGroup,
        previewTeacher, setPreviewTeacher,
        selectedTeacher,
        setSelectedTeacher,
        activeTab,
        setActiveTab,
        info,
        groupsData,
        teachersData,
        isAdmin,
        schedule,
        loading,
        loadError,
        refreshing,
        previewWeekType,
        setPreviewWeekType,
        refreshAll,
      }}
    >
      {children}
    </AppContext.Provider>
  );
};

export const useApp = () => {
  const context = useContext(AppContext);
  if (!context) throw new Error('useApp must be used within AppProvider');
  return context;
};
