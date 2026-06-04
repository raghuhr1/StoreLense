import { Navigate, Outlet } from 'react-router-dom'
import { useAppSelector }   from '@/app/hooks'
import { selectIsAuthed, selectCurrentUser } from '@/modules/auth/authSlice'
import type { Role }        from '@/types'

interface Props {
  allowedRoles?: Role[]
}

export default function PrivateRoute({ allowedRoles }: Props) {
  const isAuthed = useAppSelector(selectIsAuthed)
  const user     = useAppSelector(selectCurrentUser)

  if (!isAuthed) return <Navigate to="/login" replace />

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/403" replace />
  }

  return <Outlet />
}
