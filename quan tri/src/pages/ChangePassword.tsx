import { useState } from 'react';
import type { FormEvent } from 'react';
import { KeyRound } from 'lucide-react';
import { changePassword, getErrorMessage } from '../services/authService';
import ContentCard from '../components/ui/ContentCard';
import PageHeader from '../components/ui/PageHeader';

const ChangePassword = () => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError('');
    setSuccess('');

    if (newPassword.length < 8 || !/[A-Za-z]/.test(newPassword) || !/\d/.test(newPassword)) {
      setError('Mật khẩu mới cần tối thiểu 8 ký tự, gồm chữ và số.');
      return;
    }
    if (newPassword !== confirmation) {
      setError('Xác nhận mật khẩu mới chưa khớp.');
      return;
    }

    setSubmitting(true);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setSuccess('Đổi mật khẩu thành công.');
    } catch (requestError: unknown) {
      setError(getErrorMessage(requestError, 'Không thể đổi mật khẩu. Vui lòng thử lại.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-xl flex-col gap-5">
      <PageHeader
        eyebrow="Tài khoản / Bảo mật"
        title="Đổi mật khẩu"
        description="Cập nhật mật khẩu cho tài khoản đang đăng nhập."
      />

      <ContentCard className="p-5">
        <form onSubmit={submit} className="flex flex-col gap-5">
          <div className="grid gap-1">
            <label htmlFor="current-password" className="si-field-label">Mật khẩu hiện tại</label>
            <input id="current-password" type="password" autoComplete="current-password" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} className="si-input" />
          </div>
          <div className="grid gap-1">
            <label htmlFor="new-password" className="si-field-label">Mật khẩu mới</label>
            <input id="new-password" type="password" autoComplete="new-password" required minLength={8} value={newPassword} onChange={(event) => setNewPassword(event.target.value)} className="si-input" />
            <p className="text-2xs text-ink-muted">Tối thiểu 8 ký tự, có ít nhất một chữ và một số.</p>
          </div>
          <div className="grid gap-1">
            <label htmlFor="confirm-password" className="si-field-label">Xác nhận mật khẩu mới</label>
            <input id="confirm-password" type="password" autoComplete="new-password" required minLength={8} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} className="si-input" />
          </div>

          {error && <p role="alert" className="rounded-control bg-failed-surface text-failed px-3 py-2 text-[18px]">{error}</p>}
          {success && <p role="status" className="rounded-control bg-parsed-surface text-parsed px-3 py-2 text-[18px]">{success}</p>}

          <div className="border-hairline flex justify-end border-t pt-4">
            <button type="submit" disabled={submitting} className="si-btn-primary">
              <KeyRound size={20} />
              {submitting ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}
            </button>
          </div>
        </form>
      </ContentCard>
    </div>
  );
};

export default ChangePassword;
