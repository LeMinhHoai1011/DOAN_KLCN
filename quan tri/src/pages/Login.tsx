import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import { AlertCircle, FileSearch, ShieldCheck, Sparkles } from "lucide-react";
import AuthShell from "../components/ui/AuthShell";
import { getDashboardPath, getErrorMessage, login, logout } from "../services/authService";

const points = [
  { icon: Sparkles, title: "Trích xuất bằng AI", description: "Đọc số hóa đơn, ngày, MST và số tiền trực tiếp từ ảnh hoặc PDF." },
  { icon: FileSearch, title: "Phân loại & tra cứu", description: "Gắn loại chứng từ nghiệp vụ, lọc theo trạng thái xử lý và kiểm tra." },
  { icon: ShieldCheck, title: "Kiểm soát phân quyền", description: "Kế toán, quản trị và nhân viên dùng chung một nguồn dữ liệu được giới hạn." },
];

function Login() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");

  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");

    if (!username || !password) {
      setError("Vui lòng nhập tên đăng nhập và mật khẩu");
      return;
    }

    setIsSubmitting(true);

    try {
      const response = await login({ username, password });
      const dashboardPath = getDashboardPath(response.user);
      if (!dashboardPath) {
        logout();
        setError("Tài khoản chưa được gán vai trò hợp lệ. Vui lòng liên hệ quản trị viên.");
        return;
      }
      navigate(dashboardPath);
    } catch (error: unknown) {
      setError(getErrorMessage(error, "Đăng nhập thất bại hoặc không thể kết nối đến Server"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Đăng nhập hệ thống"
      subtitle="Sử dụng tài khoản nội bộ được cấp bởi quản trị viên."
      points={points}
      footer={
        <>
          Chưa có tài khoản?{" "}
          <Link to="/register" className="si-link font-semibold">Đăng ký</Link>
        </>
      }
    >
      <form onSubmit={handleLogin} className="flex flex-col gap-4">
        <label className="grid gap-1.5">
          <span className="si-field-label">Tên đăng nhập</span>
          <input
            type="text"
            autoComplete="username"
            placeholder="Nhập tên đăng nhập"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="si-input"
          />
        </label>

        <label className="grid gap-1.5">
          <span className="si-field-label">Mật khẩu</span>
          <input
            type="password"
            autoComplete="current-password"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="si-input"
          />
        </label>

        {error && (
          <p role="alert" className="rounded-control border-failed/25 bg-failed-surface text-failed flex items-start gap-2 border px-3 py-2 text-[17px] leading-relaxed">
            <AlertCircle size={20} className="mt-px shrink-0" />
            <span>{error}</span>
          </p>
        )}

        <button type="submit" disabled={isSubmitting} className="si-btn-primary mt-1 w-full py-2.5">
          {isSubmitting ? "Đang đăng nhập..." : "Đăng nhập"}
        </button>
      </form>
    </AuthShell>
  );
}

export default Login;
