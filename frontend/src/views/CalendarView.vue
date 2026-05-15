<template>
  <div class="calendar-view">
    <h2>{{ $t('calendar.title') }}</h2>
    <FullCalendar :options="calendarOptions" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import FullCalendar from '@fullcalendar/vue3'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import http from '@/api/http'

const router = useRouter()
const { t } = useI18n()

const calendarOptions = ref({
  plugins: [dayGridPlugin, timeGridPlugin],
  initialView: 'dayGridMonth',
  headerToolbar: {
    left: 'prev,next today',
    center: 'title',
    right: 'dayGridMonth,timeGridWeek,dayGridDay'
  },
  locale: 'zh-cn',
  events: fetchEvents,
  eventClick: (info: any) => {
    const ev = info.event
    const projectId = ev.extendedProps.projectId
    if (projectId) {
      router.push({ path: `/projects/${projectId}`, query: { todo: ev.id } })
    }
  },
  height: 'auto',
  buttonText: {
    today: t('calendar.today'),
    month: t('calendar.month'),
    week: t('calendar.week'),
    day: t('calendar.day')
  }
})

async function fetchEvents(fetchInfo: any, successCallback: (events: any[]) => void) {
  try {
    const { data } = await http.get('/todos/calendar', {
      params: {
        start: fetchInfo.startStr.slice(0, 10),
        end: fetchInfo.endStr.slice(0, 10)
      }
    })
    successCallback(data.data.map((ev: any) => ({
      id: String(ev.id),
      title: ev.title,
      start: ev.start,
      allDay: true,
      backgroundColor: ev.color,
      borderColor: ev.color,
      textColor: '#fff',
      extendedProps: {
        projectId: ev.projectId,
        projectName: ev.projectName,
        completed: ev.completed
      }
    })))
  } catch {
    successCallback([])
  }
}
</script>

<style scoped>
.calendar-view { padding: 20px; }
.calendar-view h2 { margin-bottom: 16px; }
</style>

<style>
.dark .fc .fc-daygrid-day-number,
.dark .fc .fc-col-header-cell-cushion,
.dark .fc .fc-toolbar-title,
.dark .fc .fc-day-today .fc-daygrid-day-number {
  color: var(--el-text-color-primary) !important;
}
.dark .fc-theme-standard .fc-scrollgrid,
.dark .fc-theme-standard td,
.dark .fc-theme-standard th {
  border-color: var(--el-border-color-light) !important;
}
.dark .fc .fc-day-other .fc-daygrid-day-top {
  opacity: 0.35;
}
.dark .fc .fc-button {
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
  color: var(--el-text-color-primary);
}
.dark .fc .fc-button:hover {
  background: var(--el-fill-color);
}
.dark .fc .fc-button-primary:not(:disabled).fc-button-active,
.dark .fc .fc-button-primary:not(:disabled):active {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}
.dark .fc .fc-day-today {
  background: var(--el-color-primary-light-9) !important;
}
.dark .fc .fc-non-business {
  background: var(--el-fill-color-lighter);
}
.dark .fc {
  background: var(--el-bg-color);
}
.dark .fc-theme-standard .fc-scrollgrid-section > td,
.dark .fc-theme-standard .fc-scrollgrid-section > th {
  background: var(--el-bg-color);
}
</style>
