import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import { AlertCircle, CheckCircle2, FileSearch, ShieldCheck, Sparkles } from "lucide-react";
import AuthShell from "../components/ui/AuthShell";
import { getErrorMessage, register } from "../services/authService";

const points = [
  { icon: Sparkles, title: "Trích xuất bằng AI", description: "Đọc số hóa đơn, ngày, MST và số tiền trực tiếp từ ảnh hoặc PDF." },
  { icon: FileSearch, title: "Phân loại & tra cứu", description: "Gắn loại chứng từ nghiệp vụ, lọc theo trạng thái xử lý và kiểm tra." },
  { icon: ShieldCheck, title: "Kiểm soát phân quyền", description: "Kế toán, quản trị và nhân viên dùng chung một nguồn dữ liệu được giới hạn." },
];

function Register() {
  const navigate = useNavigate();

  const [username, setUsername] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();

    setMessage("");
    setError("");

    // Kiểm tra dữ liệu
    if (!username || !fullName || !email || !password || !confirmPassword) {
      setError("Vui lòng nhập đầy đủ thông tin");
      return;
    }

    if (password !== confirmPassword) {
      setError("Mật khẩu xác nhận không khớp");
      return;
    }

    setIsSubmitting(true);

    try {
      await register({ username, fullName, email, password });

      setMessage("Đăng ký tài khoản thành công!");

      setUsername("");
      setFullName("");
      setEmail("");
      setPassword("");
      setConfirmPassword("");

      setTimeout(() => {
        navigate("/login");
      }, 1500);

    } catch (error: unknown) {
      setError(getErrorMessage(error, "Đăng ký thất bại hoặc không thể kết nối đến Server"));
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Tạo tài khoản"
      subtitle="Đăng ký tài khoản nội bộ, vai trò sẽ do quản trị viên gán sau."
      points={points}
      footer={
        <>
          Đã có tài khoản?{" "}
          <Link to="/login" className="si-link font-semibold">Đăng nhập</Link>
        </>
      }
    >
      <form onSubmit={handleRegister} className="flex flex-col gap-3.5">
        <label className="grid gap-1.5">
          <span className="si-field-label">Tên đăng nhập</span>
          <input type="text" autoComplete="username" placeholder="Nhập tên đăng nhập" value={username} onChange={(e) => setUsername(e.target.value)} className="si-input" />
        </label>

        <label className="grid gap-1.5">
          <span className="si-field-label">Họ và tên</span>
          <input type="text" autoComplete="name" placeholder="Nhập họ và tên" value={fullName} onChange={(e) => setFullName(e.target.value)} className="si-input" />
        </label>

        <label className="grid gap-1.5">
          <span className="si-field-label">Email</span>
          <input type="email" autoComplete="email" placeholder="Nhập email" value={email} onChange={(e) => setEmail(e.target.value)} className="si-input" />
        </label>

        <label className="grid gap-1.5">
          <span className="si-field-label">Mật khẩu</span>
          <input type="password" autoComplete="new-password" placeholder="Nhập mật khẩu" value={password} onChange={(e) => setPassword(e.target.value)} className="si-input" />
        </label>

        <label className="grid gap-1.5">
          <span className="si-field-label">Xác nhận mật khẩu</span>
          <input type="password" autoComplete="new-password" placeholder="Nhập lại mật khẩu" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} className="si-input" />
        </label>

        {error && (
          <p role="alert" className="rounded-control border-failed/25 bg-failed-surface text-failed flex items-start gap-2 border px-3 py-2 text-[17px] leading-relaxed">
            <AlertCircle size={20} className="mt-px shrink-0" />
            <span>{error}</span>
          </p>
        )}

        {message && (
          <p role="status" className="rounded-control border-parsed/25 bg-parsed-surface text-parsed flex items-start gap-2 border px-3 py-2 text-[17px] leading-relaxed">
            <CheckCircle2 size={20} className="mt-px shrink-0" />
            <span>{message}</span>
          </p>
        )}

        <button type="submit" disabled={isSubmitting} className="si-btn-primary mt-1 w-full py-2.5">
          {isSubmitting ? "Đang tạo tài khoản..." : "Đăng ký"}
        </button>
      </form>
    </AuthShell>
  );
}

export default Register;
