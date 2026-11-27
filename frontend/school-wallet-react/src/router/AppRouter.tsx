import { Fragment, useEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import ProtectedRoute from '../auth/ProtectedRoute';
import Login from '../pages/Login';
import Dashboard from '../pages/Dashboard';
import PocketMoney from '../pages/PocketMoney';
import CashRequests from '../pages/CashRequests';
import LibraryDesk from '../pages/LibraryDesk';
import StorePos from '../pages/StorePos';
import OperationsHub from '../pages/OperationsHub';
import TimetableGenerator from '../pages/TimetableGenerator';
import MarksUpload from '../pages/MarksUpload';
import DutyRoster from '../pages/DutyRoster';
import ResultsPlanning from '../pages/ResultsPlanning';
import ParentPortal from '../pages/ParentPortal';
import SchoolDocuments from '../pages/SchoolDocuments';
import OnlineTests from '../pages/OnlineTests';
import TakeTest from '../pages/TakeTest';
import ResourcePage from '../components/ResourcePage';
import ResourceFormPage from '../components/ResourceFormPage';
import ResourceViewPage from '../components/ResourceViewPage';
import SystemPreloader from '../components/SystemPreloader';
import { RESOURCE_CONFIGS } from '../config/resources';

function PreloaderGate({ children }: { children: ReactNode }) {
  const { loading } = useAuth();
  const [minimumShown, setMinimumShown] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => setMinimumShown(true), 900);
    return () => window.clearTimeout(timer);
  }, []);

  if (loading || !minimumShown) {
    return <SystemPreloader />;
  }
  return <>{children}</>;
}

export default function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <PreloaderGate>
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/login" element={<Login />} />
            <Route path="/test/:token" element={<TakeTest />} />
            <Route
              path="/dashboard"
              element={<ProtectedRoute path="/dashboard"><Dashboard /></ProtectedRoute>}
            />
            <Route
              path="/pocket-money"
              element={<ProtectedRoute path="/pocket-money"><PocketMoney /></ProtectedRoute>}
            />
            <Route
              path="/cash-requests"
              element={<ProtectedRoute path="/cash-requests"><CashRequests /></ProtectedRoute>}
            />
            <Route
              path="/library-desk"
              element={<ProtectedRoute path="/library-desk"><LibraryDesk /></ProtectedRoute>}
            />
            <Route
              path="/store-pos"
              element={<ProtectedRoute path="/store-pos"><StorePos /></ProtectedRoute>}
            />
            <Route
              path="/operations-hub"
              element={<ProtectedRoute path="/operations-hub"><OperationsHub /></ProtectedRoute>}
            />
            <Route
              path="/timetable-generator"
              element={<ProtectedRoute path="/timetable-generator"><TimetableGenerator /></ProtectedRoute>}
            />
            <Route
              path="/marks-upload"
              element={<ProtectedRoute path="/marks-upload"><MarksUpload /></ProtectedRoute>}
            />
            <Route
              path="/duty-roster"
              element={<ProtectedRoute path="/duty-roster"><DutyRoster /></ProtectedRoute>}
            />
            <Route
              path="/results-planning"
              element={<ProtectedRoute path="/results-planning"><ResultsPlanning /></ProtectedRoute>}
            />
            <Route
              path="/parent-portal"
              element={<ProtectedRoute path="/parent-portal"><ParentPortal /></ProtectedRoute>}
            />
            <Route
              path="/online-tests"
              element={<ProtectedRoute path="/online-tests"><OnlineTests /></ProtectedRoute>}
            />
            <Route
              path="/school-documents"
              element={<ProtectedRoute path="/school-documents"><SchoolDocuments /></ProtectedRoute>}
            />

            {Object.values(RESOURCE_CONFIGS).map((config) => (
              <Fragment key={config.resource}>
                <Route
                  path={`/${config.resource}`}
                  element={
                    <ProtectedRoute path={`/${config.resource}`}>
                      <ResourcePage config={config} />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path={`/${config.resource}/new`}
                  element={
                    <ProtectedRoute path={`/${config.resource}`}>
                      <ResourceFormPage config={config} mode="create" />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path={`/${config.resource}/:id`}
                  element={
                    <ProtectedRoute path={`/${config.resource}`}>
                      <ResourceViewPage config={config} />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path={`/${config.resource}/:id/edit`}
                  element={
                    <ProtectedRoute path={`/${config.resource}`}>
                      <ResourceFormPage config={config} mode="edit" />
                    </ProtectedRoute>
                  }
                />
              </Fragment>
            ))}

            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </PreloaderGate>
      </AuthProvider>
    </BrowserRouter>
  );
}
