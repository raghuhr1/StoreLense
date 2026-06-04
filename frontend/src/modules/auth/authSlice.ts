import { createSlice, PayloadAction } from '@reduxjs/toolkit'
import { tokenStore }                 from '@/utils/storage'
import type { AuthUser, Role }        from '@/types'

interface AuthState {
  user:         AuthUser | null
  isAuthed:     boolean
}

const initialState: AuthState = {
  user:     null,
  isAuthed: tokenStore.hasTokens(),
}

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    loginSuccess(state, { payload }: PayloadAction<{
      userId: string; username: string; role: Role; storeId: string | null
      accessToken: string; refreshToken: string
    }>) {
      tokenStore.set(payload.accessToken, payload.refreshToken)
      state.isAuthed = true
      state.user = {
        userId:   payload.userId,
        username: payload.username,
        role:     payload.role,
        storeId:  payload.storeId,
      }
    },
    setCredentials(state, { payload }: PayloadAction<{ accessToken: string; refreshToken: string }>) {
      tokenStore.set(payload.accessToken, payload.refreshToken)
    },
    logout(state) {
      tokenStore.clear()
      state.isAuthed = false
      state.user     = null
    },
  },
})

export const { loginSuccess, setCredentials, logout } = authSlice.actions
export default authSlice.reducer

export const selectCurrentUser  = (s: { auth: AuthState }) => s.auth.user
export const selectIsAuthed     = (s: { auth: AuthState }) => s.auth.isAuthed
export const selectIsAdmin      = (s: { auth: AuthState }) => s.auth.user?.role === 'ADMIN'
export const selectIsManager    = (s: { auth: AuthState }) =>
  s.auth.user?.role === 'ADMIN' || s.auth.user?.role === 'STORE_MANAGER'
