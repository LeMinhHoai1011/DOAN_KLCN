import { Link, useNavigate } from "react-router-dom";
import { useState } from "react";
import "./Login.css";

function Login() {
  const navigate = useNavigate();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");

  const [error, setError] = useState("");

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    setError("");

    if (!email || !password) {
      setError("Vui lòng nhập email và mật khẩu");
      return;
    }

    try {
      const response = await fetch(
        "http://localhost:8080/api/users/login",
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email: email,
            password: password,
          }),
        }
      );

      const data = await response.json();

      if (!response.ok) {
        setError(data);
        return;
      }

      // Lưu thông tin người dùng đang đăng nhập
      localStorage.setItem("user", JSON.stringify(data));

      // Chuyển vào Dashboard
      navigate("/");

    } catch (error) {
      setError("Không thể kết nối đến Server");
    }
  };

  return (
    <div className="login-page">
      <div className="login-box">

        <h1>SMART INVOICE</h1>
        <p className="login-title">Đăng nhập hệ thống</p>

        <form onSubmit={handleLogin}>

          <div className="form-group">
            <label>Email</label>

            <input
              type="email"
              placeholder="Nhập email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label>Mật khẩu</label>

            <input
              type="password"
              placeholder="Nhập mật khẩu"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </div>

          {error && (
            <p style={{ color: "red", marginBottom: "15px" }}>
              {error}
            </p>
          )}

          <button type="submit">
            ĐĂNG NHẬP
          </button>

        </form>

        <p className="register-text">
          Chưa có tài khoản?{" "}
          <Link to="/register">Đăng ký</Link>
        </p>

      </div>
    </div>
  );
}

export default Login;